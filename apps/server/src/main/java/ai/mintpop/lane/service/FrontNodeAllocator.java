package ai.mintpop.lane.service;

import ai.mintpop.lane.config.FrontAllocationProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.util.UsLandingNodes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 前置节点分配：按故障域分桶，每桶取 K 个。
 *
 * 冗余必须按故障域分散，而不是按节点数量分散：同一故障域下的节点共用一台中转
 * 入口机，入口一挂它们一起挂，彼此不构成冗余。只有跨故障域才是真冗余。
 * 算法见 spec 《第一跳稳定性与可观测性》§7.2。
 */
@Service
public class FrontNodeAllocator {

    private final ProxyNodeRepository nodeRepository;
    private final UserFrontNodeRepository userFrontNodeRepository;
    private final FrontAllocationProperties properties;

    public FrontNodeAllocator(ProxyNodeRepository nodeRepository,
                               UserFrontNodeRepository userFrontNodeRepository,
                               FrontAllocationProperties properties) {
        this.nodeRepository = nodeRepository;
        this.userFrontNodeRepository = userFrontNodeRepository;
        this.properties = properties;
    }

    /**
     * 给指定用户分配一组前置节点。
     * userId 目前只用于将来扩展（如按用户做亲和性调度），当前分配结果与用户身份无关，
     * 纯按全局负载与故障域摊平。
     */
    public AllocationResult allocate(Long userId) {
        List<ProxyNodeDto> candidates = nodeRepository.findAll(NodeRole.FRONT).stream()
                .filter(node -> node.getStatus() == NodeStatus.ENABLED)
                .filter(node -> node.getFailureDomain() != null)
                .filter(node -> UsLandingNodes.isUsLanding(node.getName()))
                .toList();

        if (candidates.isEmpty()) {
            return new AllocationResult(List.of(), null, 0);
        }

        // 按故障域名字典序分桶，遍历顺序必须稳定：否则同一份数据两次分配结果不同，出问题时无法复现
        Map<String, List<ProxyNodeDto>> buckets = new TreeMap<>();
        for (ProxyNodeDto node : candidates) {
            buckets.computeIfAbsent(node.getFailureDomain(), k -> new ArrayList<>()).add(node);
        }

        Map<Long, Long> loadByNodeId = userFrontNodeRepository.countUsersByNodeId();
        Comparator<ProxyNodeDto> byLoadThenId = Comparator
                .<ProxyNodeDto>comparingLong(node -> loadByNodeId.getOrDefault(node.getId(), 0L))
                .thenComparing(ProxyNodeDto::getId);

        List<Long> nodeIds = new ArrayList<>();
        Long primaryNodeId = null;
        for (List<ProxyNodeDto> bucket : buckets.values()) {
            List<Long> picked = bucket.stream()
                    .sorted(byLoadThenId)
                    .limit(properties.getNodesPerDomain())
                    .map(ProxyNodeDto::getId)
                    .toList();
            if (primaryNodeId == null && !picked.isEmpty()) {
                primaryNodeId = picked.getFirst();
            }
            nodeIds.addAll(picked);
        }

        return new AllocationResult(nodeIds, primaryNodeId, buckets.size());
    }

    /** 分配结果：nodeIds 是要写入 user_front_node 的完整集合，primaryNodeId 是其中的首选（写入 front_node_id） */
    public record AllocationResult(List<Long> nodeIds, Long primaryNodeId, int failureDomainCount) {
    }
}
