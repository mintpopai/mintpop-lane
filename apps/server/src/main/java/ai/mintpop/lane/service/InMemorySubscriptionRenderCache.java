package ai.mintpop.lane.service;

import ai.mintpop.lane.config.FrontTuningProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.repository.ProxyNodeRepository;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 进程内实现：ConcurrentHashMap，无 TTL——节点只经订阅对齐与删除两条路径改动，两处都会 evict */
public class InMemorySubscriptionRenderCache implements SubscriptionRenderCache {

    private final ProxyNodeRepository nodeRepository;
    private final FrontTuningProperties frontTuningProperties;
    private final SystemSettingService systemSettingService;
    private final Map<Long, RenderedSubscription> cache = new ConcurrentHashMap<>();

    public InMemorySubscriptionRenderCache(ProxyNodeRepository nodeRepository, FrontTuningProperties frontTuningProperties,
                                           SystemSettingService systemSettingService) {
        this.nodeRepository = nodeRepository;
        this.frontTuningProperties = frontTuningProperties;
        this.systemSettingService = systemSettingService;
    }

    @Override
    public RenderedSubscription get(Long airportSubscriptionId) {
        return cache.computeIfAbsent(airportSubscriptionId, this::render);
    }

    /**
     * 立即逐出，并在当前有事务时于提交后再逐出一次。调用方（订阅对齐、订阅删除）是在事务内改库的，
     * 只在提交前逐出的话，提交前的空档里任何心跳/下发都会读到未提交前的旧节点并把旧渲染写回缓存
     * （无 TTL），用户就一直拿旧 configVersion、热更新不触发。提交后再删一次即从另一侧关上这个窗口。
     * 逻辑只放在这里，各调用点无需关心事务。
     */
    @Override
    public void evict(Long airportSubscriptionId) {
        cache.remove(airportSubscriptionId);
        afterCommitOrNow(() -> cache.remove(airportSubscriptionId));
    }

    @Override
    public void evictAll() {
        cache.clear();
        afterCommitOrNow(cache::clear);
    }

    /** 有事务同步就挂到提交后；没有事务时上面已立即逐出，无需再做 */
    private static void afterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private RenderedSubscription render(Long airportSubscriptionId) {
        NodeRegion region = systemSettingService.frontSettings().region();
        List<ProxyNodeDto> usable = nodeRepository.findByAirportSubscriptionId(airportSubscriptionId).stream()
                .filter(node -> region.matches(node.getSourceName()))
                .toList();
        List<Map<String, Object>> nodes = usable.stream()
                // 保活参数覆盖对组里每个节点都要套，否则组内切换过去就退化成每请求重握手
                .map(node -> node.toMihomoNode(tuningOf(node)))
                .toList();
        return new RenderedSubscription(mostCommonFailureDomain(usable), nodes);
    }

    private Map<String, Object> tuningOf(ProxyNodeDto front) {
        if (front.getSourceType() == null) {
            return Map.of();
        }
        return frontTuningProperties.getProtocols().getOrDefault(front.getSourceType(), Map.of());
    }

    /** 出现次数最多的故障域；平手取字典序最小；全未解析返回 null。只作上报关联键 */
    static String mostCommonFailureDomain(List<ProxyNodeDto> nodes) {
        return nodes.stream()
                .map(ProxyNodeDto::getFailureDomain)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .reduce((best, next) -> next.getValue() > best.getValue() ? next : best)
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
