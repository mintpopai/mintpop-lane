package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.LinkAlertState;
import ai.mintpop.lane.repository.AsnOrgRepository;
import ai.mintpop.lane.repository.LinkAlertStateRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.LinkReportRepository.DomainAsnAggregate;
import ai.mintpop.lane.repository.LinkReportRepository.UserDomainAsnAggregate;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 成功率跌破阈值告警：对同一用户的「故障域 × 运营商」聚合结果分别做故障域级（跨运营商求和）
 * 与运营商级（单行）两种独立判定，各自独立去重——两者共用 {@code link_alert_state} 表，
 * 故障域级那行的 {@code asn} 存空串（同源思路见 {@link TrafficAlertService} 把「已推到哪一档」记在列上）。
 * <p>
 * 运营商维度的键是 <b>ASN</b>：分组、去重键、判定全认 ASN，展示名只在推送文案上露面——
 * 每轮 {@link #checkAll()} 从 {@code asn_org} 读一次全表（几十行），整轮复用，不逐个用户查库。
 * <p>
 * 判定顺序固定为「先看样本量、再算比值」：{@code aggregateByDomainAndAsn} 传入的样本可能是 0
 * （整段窗口离线的用户），先除后判会在这里抛 {@link ArithmeticException} 或算出 NaN；
 * 样本低于 {@link LinkReportProperties#getAlertMinSamples()} 时比值本身没有意义，
 * 按 0/0 当成 0% 报出去会在最不该吵的时候吵。
 */
@Slf4j
@Service
public class LinkReportAlertService {

    private final LinkReportRepository linkReportRepository;
    private final LinkAlertStateRepository alertStateRepository;
    private final UserRepository userRepository;
    private final AsnOrgRepository asnOrgRepository;
    private final NodeNotifyService notifyService;
    private final LinkReportProperties properties;
    private final Clock clock;

    public LinkReportAlertService(LinkReportRepository linkReportRepository,
                                  LinkAlertStateRepository alertStateRepository, UserRepository userRepository,
                                  AsnOrgRepository asnOrgRepository, NodeNotifyService notifyService,
                                  LinkReportProperties properties, Clock clock) {
        this.linkReportRepository = linkReportRepository;
        this.alertStateRepository = alertStateRepository;
        this.userRepository = userRepository;
        this.asnOrgRepository = asnOrgRepository;
        this.notifyService = notifyService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 定时扫描全部用户：按 {@link LinkReportProperties#getAlertLookback()} 回看窗口，
     * 从全库聚合（SQL 层 GROUP BY，见 {@link LinkReportRepository#aggregateAllUsersByDomainAndAsn}）
     * 里按用户切分后逐个调用 {@link #checkAndNotify}。形态照抄 {@link EntryIpWatchService}：
     * fixedDelay 让上一轮跑完再计时，initialDelay 同样取周期，避免每次重启都立刻扫一遍全库。
     * 单个用户处理失败（多半是去重状态落库异常）只记日志、跳过该用户，不影响其余用户被扫到。
     */
    @Scheduled(fixedDelayString = "#{@linkReportProperties.alertCheckInterval.toMillis()}",
            initialDelayString = "#{@linkReportProperties.alertCheckInterval.toMillis()}")
    public void checkAll() {
        Instant now = clock.instant();
        Instant from = now.minus(properties.getAlertLookback());

        Map<Long, List<UserDomainAsnAggregate>> byUser = linkReportRepository
                .aggregateAllUsersByDomainAndAsn(from, now).stream()
                .collect(Collectors.groupingBy(UserDomainAsnAggregate::userId));

        // 展示名整轮只读一次全表（几十行）：它只用于拼推送文案，不参与任何判定，
        // 放进循环里逐个用户查就是把一次查询乘上用户数，白打一堆库
        Map<String, String> orgNames = resolveOrgNames();

        for (Map.Entry<Long, List<UserDomainAsnAggregate>> entry : byUser.entrySet()) {
            Long userId = entry.getKey();
            List<DomainAsnAggregate> aggregates = entry.getValue().stream()
                    .map(row -> new DomainAsnAggregate(row.failureDomain(), row.asn(), row.samples(),
                            row.aliveCount(), row.failovers()))
                    .toList();
            try {
                checkAndNotify(userId, orgNames, aggregates);
            } catch (Exception e) {
                log.warn("链路成功率告警扫描失败，跳过 userId={}", userId, e);
            }
        }
    }

    /**
     * 对一个用户在某个窗口区间内「故障域 × 运营商」的聚合结果做告警判定。
     * 先按故障域把各运营商的样本/存活数求和做故障域级判定，再对每一行单独做运营商级判定——
     * 两条判定互不依赖，一个的去重状态不影响另一个。
     * <p>
     * 按用户告警，不跨用户聚合：只有一个用户劣化多半是他的分配或本地网络问题，跨用户聚合
     * 会稀释这个信号；去重按 (user, domain, asn)，同一用户在恢复之前只推一次，不会刷屏。
     *
     * @param orgNames 本轮的 ASN → 展示名快照（{@link AsnOrgRepository#findAllNames()}），
     *                 由调用方每轮查一次后整轮复用；只用于拼推送文案，不参与任何判定，
     *                 查不到该 ASN 时取到 null，通知那头会退回展示 ASN
     */
    public void checkAndNotify(Long userId, Map<String, String> orgNames, List<DomainAsnAggregate> aggregates) {
        String email = resolveEmail(userId);

        Map<String, List<DomainAsnAggregate>> byDomain = new LinkedHashMap<>();
        for (DomainAsnAggregate aggregate : aggregates) {
            byDomain.computeIfAbsent(aggregate.failureDomain(), domain -> new ArrayList<>())
                    .add(aggregate);
        }

        for (Map.Entry<String, List<DomainAsnAggregate>> entry : byDomain.entrySet()) {
            String domain = entry.getKey();
            List<DomainAsnAggregate> rows = entry.getValue();

            long domainSamples = rows.stream().mapToLong(DomainAsnAggregate::samples).sum();
            long domainAlive = rows.stream().mapToLong(DomainAsnAggregate::aliveCount).sum();
            evaluateDomainLevel(userId, email, domain, domainSamples, domainAlive);

            for (DomainAsnAggregate row : rows) {
                // asn == null（ASN 反查失败）时刻意跳过运营商级判定，不只是"没意义"：
                // 本表 asn 是 NOT NULL DEFAULT ''，故障域级那行的去重键固定是 (domain, "")，
                // 若把反查失败的运营商级判定也落到同一个 ""，两者会共用同一行去重状态——
                // 谁先跑谁的告警状态就把对方的判定"吃掉"（先到者置 alerted=1，后到者看到
                // 已告警直接判去重，导致其中一种通知被永久吞掉）。落地时靠这条测试
                // （unresolvedAsnAggregateSkipsAsnLevelToAvoidKeyCollisionWithDomainLevel）抓到过一次真实吞没。
                if (row.asn() != null) {
                    evaluateAsnLevel(userId, email, domain, row.asn(), orgNames.get(row.asn()),
                            row.samples(), row.aliveCount());
                }
            }
        }
    }

    /**
     * 查本轮的 ASN → 展示名快照；查询异常一律 fail-soft 返回空 Map，文案随之退回展示 ASN。
     * <p>
     * 这一句在 per-user 的 try <b>之外</b>（整轮只查一次），所以它自己必须兜住异常：否则一次
     * 查询失败会让这一轮<b>全部用户</b>的告警判定被整个跳过，而代价仅仅是消息里少了一行名字。
     * 取舍与 {@link #resolveEmail} 完全一致——告警本身比消息里那行展示文字重要得多。
     */
    private Map<String, String> resolveOrgNames() {
        try {
            return asnOrgRepository.findAllNames();
        } catch (Exception e) {
            log.warn("查询 ASN 展示名失败，本轮告警文案退回展示 ASN", e);
            return Map.of();
        }
    }

    /**
     * 查用户邮箱供告警消息展示；查询异常或查不到都 fail-soft 返回 null，绝不能因为这一步
     * 失败就让整个用户的告警判定跟着中断——告警本身比消息里那行展示文字重要得多。
     */
    private String resolveEmail(Long userId) {
        try {
            return userRepository.findById(userId).map(UserDto::getEmail).orElse(null);
        } catch (Exception e) {
            log.warn("查询用户邮箱失败，告警消息将展示为「用户 #{}」userId={}", userId, userId, e);
            return null;
        }
    }

    /** 故障域级判定：运营商维度不存在，去重键的 asn 段固定存空串 */
    private void evaluateDomainLevel(Long userId, String email, String domain, long samples, long aliveCount) {
        evaluate(userId, email, domain, "", null, samples, aliveCount, true);
    }

    /** 运营商级判定：调用方保证 asn 非 null（反查失败的行已在 {@link #checkAndNotify} 里跳过） */
    private void evaluateAsnLevel(Long userId, String email, String domain, String asn, String orgName,
                                  long samples, long aliveCount) {
        evaluate(userId, email, domain, asn, orgName, samples, aliveCount, false);
    }

    /**
     * @param asnKey      去重键的运营商段：运营商级判定是 ASN 本身，故障域级固定是空串
     * @param orgName     该 ASN 的展示名，只传给通知拼文案（{@code asn_org} 里还没记过名字时为 null）；
     *                    domainLevel=true 时未使用
     * @param domainLevel true 表示这是故障域级判定，调用 {@code notifyFailureDomainDegraded}；
     *                    false 表示运营商级，调用 {@code notifyAsnDegraded}
     */
    private void evaluate(Long userId, String email, String domain, String asnKey, String orgName, long samples,
                          long aliveCount, boolean domainLevel) {
        // 先看样本量，再算比值：samples<=0 时直接跳过，避免除以零；样本不足门槛时比值没有意义，
        // 既不告警也不动已有的去重状态——样本太少什么都判断不出，不该被当成"已恢复"而清档
        if (samples <= 0 || samples < properties.getAlertMinSamples()) {
            return;
        }

        double successRate = (double) aliveCount / samples;
        boolean degraded = successRate < properties.getAlertThreshold();

        LinkAlertState state = alertStateRepository.find(userId, domain, asnKey).orElseGet(() -> {
            LinkAlertState fresh = new LinkAlertState();
            fresh.setUserId(userId);
            fresh.setFailureDomain(domain);
            fresh.setAsn(asnKey);
            fresh.setAlerted(false);
            return fresh;
        });
        // 已告警状态是否仍在去重有效期内：超过 alertDedupTtl 仍处于劣化，视同未告警重新推一次——
        // 用户在劣化中离线（最常见：链路差到断开）后再回来仍劣化，没有这条永远收不到第二次告警
        Instant now = clock.instant();
        boolean previouslyAlerted = Boolean.TRUE.equals(state.getAlerted())
                && state.getAlertedAt() != null
                && state.getAlertedAt().plus(properties.getAlertDedupTtl()).isAfter(now);

        if (degraded) {
            if (previouslyAlerted) {
                return; // 已经推过且尚未恢复，去重，不重复推送
            }
            state.setAlerted(true);
            state.setAlertedAt(now);
            // 先落库再通知：通知失败不该让去重状态丢失，否则下一轮会重复推
            alertStateRepository.upsert(state);
            try {
                if (domainLevel) {
                    notifyService.notifyFailureDomainDegraded(userId, email, domain, successRate, samples);
                } else {
                    notifyService.notifyAsnDegraded(userId, email, domain, asnKey, orgName, successRate, samples);
                }
            } catch (Exception e) {
                log.warn("链路成功率告警推送失败（去重状态已落库）userId={} domain={} asn={}",
                        userId, domain, asnKey, e);
            }
        } else if (previouslyAlerted) {
            // 恢复正常：清档，下次再劣化能重新推
            state.setAlerted(false);
            alertStateRepository.upsert(state);
        }
    }
}
