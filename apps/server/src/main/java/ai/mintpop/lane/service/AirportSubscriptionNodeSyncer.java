package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一个订阅的 FRONT 节点集合整体对齐到本次拉取结果：匹配上的（按订阅原始名 sourceName）原地更新参数，
 * 订阅里新出现的入库，库里有但订阅里没有的删除。导入与 5 分钟刷新共用这一份逻辑。
 * <p>
 * 二期起 FRONT 节点没有状态、用户引用的是订阅而不是节点，所以删除是安全的；
 * 名称/备注是管理员的手工痕迹，匹配上时不动。调用方必须在事务内调用。
 */
@Component
public class AirportSubscriptionNodeSyncer {

    static final int NODE_NAME_MAX_CODE_POINTS = 64;

    private final ProxyNodeRepository nodeRepository;
    private final FailureDomainSyncer failureDomainSyncer;

    public AirportSubscriptionNodeSyncer(ProxyNodeRepository nodeRepository, FailureDomainSyncer failureDomainSyncer) {
        this.nodeRepository = nodeRepository;
        this.failureDomainSyncer = failureDomainSyncer;
    }

    public record SyncResult(List<String> added, List<String> removed, int updated) {
    }

    /** 从解析结果里挑出要入库的节点：剔掉伪条目（「剩余流量」之类），只留当前地区，按 sourceName 去重保序 */
    public List<SubNode> selectRegionNodes(List<SubNode> parsed, NodeRegion region) {
        Map<String, SubNode> byName = new LinkedHashMap<>();
        parsed.stream()
                .filter(node -> !node.suspectedInfo() && region.matches(node.sourceName()))
                .forEach(node -> byName.putIfAbsent(node.sourceName(), node));
        return List.copyOf(byName.values());
    }

    public SyncResult sync(Long airportSubscriptionId, List<SubNode> regionNodes, Map<String, String> failureDomains) {
        Map<String, ProxyNodeDto> existingByName = new LinkedHashMap<>();
        nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)
                .forEach(node -> existingByName.putIfAbsent(node.getSourceName(), node));

        List<String> added = new ArrayList<>();
        int updated = 0;
        for (SubNode sub : regionNodes) {
            ProxyNodeDto existing = existingByName.remove(sub.sourceName());
            if (existing == null) {
                nodeRepository.create(newNode(airportSubscriptionId, sub, failureDomains));
                added.add(sub.sourceName());
            } else {
                existing.setServerAddr(sub.serverAddr());
                existing.setPort(sub.port());
                existing.setSourceType(sub.sourceType());
                existing.setSecret(sub.params());
                failureDomainSyncer.apply(existing, sub.serverAddr(), failureDomains);
                nodeRepository.update(existing);
                updated++;
            }
        }
        // 剩下没被匹配到的就是订阅里已经消失的节点
        List<String> removed = new ArrayList<>();
        for (ProxyNodeDto vanished : existingByName.values()) {
            nodeRepository.deleteById(vanished.getId());
            removed.add(vanished.getSourceName());
        }
        return new SyncResult(added, removed, updated);
    }

    private ProxyNodeDto newNode(Long airportSubscriptionId, SubNode sub, Map<String, String> failureDomains) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName(uniqueNodeName(sub.sourceName()));
        // 订阅导入的节点一律是前置节点：机场几乎不提供 HTTP 代理，落地节点需要独占的干净出口 IP
        node.setRole(NodeRole.FRONT);
        node.setProtocol(NodeProtocol.MIHOMO);
        node.setServerAddr(sub.serverAddr());
        node.setPort(sub.port());
        node.setExtraConfig(Map.of());
        node.setSecret(sub.params());
        node.setAirportSubscriptionId(airportSubscriptionId);
        node.setSourceName(sub.sourceName());
        node.setSourceType(sub.sourceType());
        failureDomainSyncer.apply(node, sub.serverAddr(), failureDomains);
        return node;
    }

    /** 撞全局唯一名时加「 (2)」「 (3)」后缀；按码点截断，不把 emoji 劈成半个代理对 */
    private String uniqueNodeName(String sourceName) {
        String base = truncateByCodePoints(sourceName, NODE_NAME_MAX_CODE_POINTS);
        if (!nodeRepository.existsByName(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String suffix = " (" + i + ")";
            String candidate = truncateByCodePoints(base, NODE_NAME_MAX_CODE_POINTS - suffix.length()) + suffix;
            if (!nodeRepository.existsByName(candidate)) {
                return candidate;
            }
        }
    }

    private static String truncateByCodePoints(String s, int maxCodePoints) {
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints));
    }
}
