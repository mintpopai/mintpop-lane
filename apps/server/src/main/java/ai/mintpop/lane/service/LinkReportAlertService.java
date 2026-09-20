package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkAlertState;
import ai.mintpop.lane.repository.LinkAlertStateRepository;
import ai.mintpop.lane.repository.LinkReportRepository.DomainIspAggregate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成功率跌破阈值告警：对同一用户的「故障域 × 运营商」聚合结果分别做故障域级（跨运营商求和）
 * 与运营商级（单行）两种独立判定，各自独立去重——两者共用 {@code link_alert_state} 表，
 * 故障域级那行的 {@code isp} 存空串（同源思路见 {@link TrafficAlertService} 把「已推到哪一档」记在列上）。
 * <p>
 * 判定顺序固定为「先看样本量、再算比值」：{@code aggregateByDomainAndIsp} 传入的样本可能是 0
 * （整段窗口离线的用户），先除后判会在这里抛 {@link ArithmeticException} 或算出 NaN；
 * 样本低于 {@link LinkReportProperties#getAlertMinSamples()} 时比值本身没有意义，
 * 按 0/0 当成 0% 报出去会在最不该吵的时候吵。
 */
@Slf4j
@Service
public class LinkReportAlertService {

    private final LinkAlertStateRepository alertStateRepository;
    private final NodeNotifyService notifyService;
    private final LinkReportProperties properties;
    private final Clock clock;

    public LinkReportAlertService(LinkAlertStateRepository alertStateRepository, NodeNotifyService notifyService,
                                  LinkReportProperties properties, Clock clock) {
        this.alertStateRepository = alertStateRepository;
        this.notifyService = notifyService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 对一个用户在某个窗口区间内「故障域 × 运营商」的聚合结果做告警判定。
     * 先按故障域把各运营商的样本/存活数求和做故障域级判定，再对每一行单独做运营商级判定——
     * 两条判定互不依赖，一个的去重状态不影响另一个。
     */
    public void checkAndNotify(Long userId, List<DomainIspAggregate> aggregates) {
        Map<String, List<DomainIspAggregate>> byDomain = new LinkedHashMap<>();
        for (DomainIspAggregate aggregate : aggregates) {
            byDomain.computeIfAbsent(aggregate.failureDomain(), domain -> new ArrayList<>())
                    .add(aggregate);
        }

        for (Map.Entry<String, List<DomainIspAggregate>> entry : byDomain.entrySet()) {
            String domain = entry.getKey();
            List<DomainIspAggregate> rows = entry.getValue();

            long domainSamples = rows.stream().mapToLong(DomainIspAggregate::samples).sum();
            long domainAlive = rows.stream().mapToLong(DomainIspAggregate::aliveCount).sum();
            evaluateDomainLevel(userId, domain, domainSamples, domainAlive);

            for (DomainIspAggregate row : rows) {
                // isp == null（ASN 反查失败）时刻意跳过运营商级判定，不只是"没意义"：
                // 本表 isp 是 NOT NULL DEFAULT ''，故障域级那行的去重键固定是 (domain, "")，
                // 若把反查失败的运营商级判定也落到同一个 ""，两者会共用同一行去重状态——
                // 谁先跑谁的告警状态就把对方的判定"吃掉"（先到者置 alerted=1，后到者看到
                // 已告警直接判去重，导致其中一种通知被永久吞掉）。落地时靠这条测试
                // （unresolvedIspAggregateSkipsIspLevelToAvoidKeyCollisionWithDomainLevel）抓到过一次真实吞没。
                if (row.isp() != null) {
                    evaluateIspLevel(userId, domain, row.isp(), row.samples(), row.aliveCount());
                }
            }
        }
    }

    /** 故障域级判定：isp 维度不存在，去重键的 isp 段固定存空串 */
    private void evaluateDomainLevel(Long userId, String domain, long samples, long aliveCount) {
        evaluate(userId, domain, "", samples, aliveCount, true, null);
    }

    /** 运营商级判定：调用方保证 isp 非 null（反查失败的行已在 {@link #checkAndNotify} 里跳过） */
    private void evaluateIspLevel(Long userId, String domain, String isp, long samples, long aliveCount) {
        evaluate(userId, domain, isp, samples, aliveCount, false, isp);
    }

    /**
     * @param domainLevel  true 表示这是故障域级判定，调用 {@code notifyFailureDomainDegraded}；
     *                     false 表示运营商级，调用 {@code notifyIspDegraded}
     * @param ispForNotify 传给 notify 方法展示用的原始 isp（可能是 null，表示 ASN 反查失败）；
     *                     domainLevel=true 时未使用
     */
    private void evaluate(Long userId, String domain, String ispKey, long samples, long aliveCount,
                          boolean domainLevel, String ispForNotify) {
        // 先看样本量，再算比值：samples<=0 时直接跳过，避免除以零；样本不足门槛时比值没有意义，
        // 既不告警也不动已有的去重状态——样本太少什么都判断不出，不该被当成"已恢复"而清档
        if (samples <= 0 || samples < properties.getAlertMinSamples()) {
            return;
        }

        double successRate = (double) aliveCount / samples;
        boolean degraded = successRate < properties.getAlertThreshold();

        LinkAlertState state = alertStateRepository.find(userId, domain, ispKey).orElseGet(() -> {
            LinkAlertState fresh = new LinkAlertState();
            fresh.setUserId(userId);
            fresh.setFailureDomain(domain);
            fresh.setIsp(ispKey);
            fresh.setAlerted(false);
            return fresh;
        });
        boolean previouslyAlerted = Boolean.TRUE.equals(state.getAlerted());

        if (degraded) {
            if (previouslyAlerted) {
                return; // 已经推过且尚未恢复，去重，不重复推送
            }
            state.setAlerted(true);
            state.setAlertedAt(clock.instant());
            // 先落库再通知：通知失败不该让去重状态丢失，否则下一轮会重复推
            alertStateRepository.upsert(state);
            try {
                if (domainLevel) {
                    notifyService.notifyFailureDomainDegraded(domain, successRate, samples);
                } else {
                    notifyService.notifyIspDegraded(domain, ispForNotify, successRate, samples);
                }
            } catch (Exception e) {
                log.warn("链路成功率告警推送失败（去重状态已落库）userId={} domain={} isp={}",
                        userId, domain, ispKey, e);
            }
        } else if (previouslyAlerted) {
            // 恢复正常：清档，下次再劣化能重新推
            state.setAlerted(false);
            alertStateRepository.upsert(state);
        }
    }
}
