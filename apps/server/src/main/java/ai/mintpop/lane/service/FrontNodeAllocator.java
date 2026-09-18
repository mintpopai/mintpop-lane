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
import java.util.Set;
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
     * <p>
     * <b>对同一用户是幂等的</b>：统计负载时排除他自己当前占着的节点。
     * {@link UserFrontNodeRepository#countUsersByNodeId()} 是全表统计，把该用户自己也算在内——
     * 重新分配时他正在用的那几个节点各自多算 1 次负载、被排到队尾，算法就会挑一组<b>全新的</b>
     * 节点给他，连续两次分配等于连续两次整组更换。而客户端选 {@code fallback} 而不是
     * {@code url-test} 正是为了粘性（spec §5.2）：整组变脸意味着全部重新握手，
     * 与这一期要治的「把瞬时抖动放大成硬故障」方向相反。
     * 排除自身之后，「同一份数据两次分配结果相同」才真的成立（见本类下面关于遍历顺序的说明）。
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
        // 该用户自己当前占着的节点：它们在全表统计里各自含着他这一份，比较负载前要先扣掉，
        // 否则重新分配会系统性地避开他现在用得好好的那几个（见方法注释）
        Set<Long> ownNodeIds = Set.copyOf(userFrontNodeRepository.findNodeIdsByUserId(userId));
        Comparator<ProxyNodeDto> byLoadThenId = Comparator
                .<ProxyNodeDto>comparingLong(node -> loadExcludingSelf(node, loadByNodeId, ownNodeIds))
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

    /** 该节点已分配的用户数，扣除本次分配的这位用户自己（他占着的名额在重新分配时不该算作阻力） */
    private static long loadExcludingSelf(ProxyNodeDto node, Map<Long, Long> loadByNodeId,
                                           Set<Long> ownNodeIds) {
        long load = loadByNodeId.getOrDefault(node.getId(), 0L);
        return ownNodeIds.contains(node.getId()) ? load - 1 : load;
    }

    /** 分配结果：nodeIds 是要写入 user_front_node 的完整集合，primaryNodeId 是其中的首选（写入 front_node_id） */
    public record AllocationResult(List<Long> nodeIds, Long primaryNodeId, int failureDomainCount) {
    }
}
