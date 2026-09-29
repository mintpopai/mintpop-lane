package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 订阅定时刷新（默认每 5 分钟）：把每个订阅的 FRONT 节点集合整体对齐到机场当前给出的节点，
 * 同时更新额度信息。二期起 FRONT 节点没有状态，拉到哪些当前地区的节点就用哪些，不再需要人决定启用谁。
 * <p>
 * 失败处理是刻意保守的：某个订阅拉不到（经 {@link SubFetchClient} 的重试装饰器仍失败），
 * 或解析出来当前地区一个节点都没有，**都不动它的节点**——拉不到不等于节点没了；只在订阅上记
 * fetchFailedSince / lastFetchError，并每轮推一条飞书（不去重，让人一直看到它没好）。
 * 其它订阅照常继续。全体重算会调用 {@link #refreshAllNow()} 并据失败名单决定是否中止。
 */
@Slf4j
@Service
public class SubRefreshService {

    private final AirportSubscriptionRepository airportSubscriptionRepository;
        private final SubFetchClient subFetchClient;
    private final SubYamlParser subYamlParser;
    private final FailureDomainSyncer failureDomainSyncer;
    private final AirportSubscriptionNodeSyncer nodeSyncer;
    private final NodeNotifyService nodeNotifyService;
    private final TrafficAlertService trafficAlertService;
    private final SystemSettingService systemSettingService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public SubRefreshService(AirportSubscriptionRepository airportSubscriptionRepository,
                             SubFetchClient subFetchClient, SubYamlParser subYamlParser,
                             FailureDomainSyncer failureDomainSyncer, AirportSubscriptionNodeSyncer nodeSyncer,
                             NodeNotifyService nodeNotifyService, TrafficAlertService trafficAlertService,
                             SystemSettingService systemSettingService, TransactionTemplate transactionTemplate, Clock clock) {
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.failureDomainSyncer = failureDomainSyncer;
        this.nodeSyncer = nodeSyncer;
        this.nodeNotifyService = nodeNotifyService;
        this.trafficAlertService = trafficAlertService;
        this.systemSettingService = systemSettingService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /** 一轮刷新的结果：拉取失败（或无当前地区节点）的订阅名，按遍历顺序 */
    public record RefreshOutcome(List<String> failedSubscriptionNames) {
    }

    /** fixedDelay：上一轮跑完再计时，订阅多、拉取慢也不会两轮叠在一起；启动后先等一轮 */
    @Scheduled(fixedDelayString = "#{@subRefreshProperties.interval.toMillis()}",
            initialDelayString = "#{@subRefreshProperties.interval.toMillis()}")
    public void refreshAll() {
        refreshAllNow();
    }

    /** 同步刷新全部订阅并返回失败名单；全体重算在拉取阶段调它 */
    public RefreshOutcome refreshAllNow() {
        NodeRegion region = systemSettingService.frontSettings().region();
        List<String> failed = new ArrayList<>();
        for (AirportSubscriptionDto group : airportSubscriptionRepository.findAll()) {
            try {
                refreshOne(group, region);
            } catch (BizException e) {
                markFailed(group, e.getMessage());
                failed.add(group.getName());
            } catch (RuntimeException e) {
                log.warn("订阅刷新出现未预期异常 airportSubscriptionId={} name={}", group.getId(), group.getName(), e);
                markFailed(group, "刷新异常：" + e.getClass().getSimpleName());
                failed.add(group.getName());
            }
        }
        return new RefreshOutcome(List.copyOf(failed));
    }

    private void refreshOne(AirportSubscriptionDto group, NodeRegion region) {
        // 拉取（HTTP）与故障域解析（DNS）都是外呼，必须在任何数据库写入之前完成，不能包进事务
        SubFetchResult result = subFetchClient.fetch(group.getSubUrl());
        List<SubNode> selected = nodeSyncer.selectRegionNodes(subYamlParser.parse(result.body()), region);
        if (selected.isEmpty()) {
            throw new BizException(BizCodeEnum.SUB_NO_REGION_NODES);
        }
        Map<String, String> failureDomains = failureDomainSyncer.resolve(selected);

        group.setUsedBytes(result.usedBytes());
        group.setTotalBytes(result.totalBytes());
        group.setExpiresAt(result.expiresAt());
        group.setFetchedAt(clock.instant());
        group.setFetchFailedSince(null);
        group.setLastFetchError(null);

        transactionTemplate.executeWithoutResult(status -> {
            airportSubscriptionRepository.update(group);
            nodeSyncer.sync(group.getId(), selected, failureDomains);
        });

        trafficAlertService.checkAndNotify(group, result);
    }

    /** 首次失败记起始时间，连续失败保留首次；错误说明截到列宽；每轮都推飞书 */
    private void markFailed(AirportSubscriptionDto group, String error) {
        Instant now = clock.instant();
        if (group.getFetchFailedSince() == null) {
            group.setFetchFailedSince(now);
        }
        group.setLastFetchError(error.length() > 255 ? error.substring(0, 255) : error);
        airportSubscriptionRepository.update(group);
        log.warn("订阅拉取失败 airportSubscriptionId={} name={} since={} error={}",
                group.getId(), group.getName(), group.getFetchFailedSince(), error);
        nodeNotifyService.notifySubFetchFailed(group, error, group.getFetchFailedSince());
    }
}
