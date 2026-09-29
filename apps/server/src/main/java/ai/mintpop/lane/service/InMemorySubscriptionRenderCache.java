package ai.mintpop.lane.service;

import ai.mintpop.lane.config.FrontTuningProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.repository.ProxyNodeRepository;

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

    @Override
    public void evict(Long airportSubscriptionId) {
        cache.remove(airportSubscriptionId);
    }

    @Override
    public void evictAll() {
        cache.clear();
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
