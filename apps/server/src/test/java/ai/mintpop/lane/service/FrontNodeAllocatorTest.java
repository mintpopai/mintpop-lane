package ai.mintpop.lane.service;

import ai.mintpop.lane.config.FrontAllocationProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("前置节点分配：按故障域分桶，每桶取 K 个")
class FrontNodeAllocatorTest {

    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private UserFrontNodeRepository userFrontNodeRepository;

    private FrontNodeAllocator allocator;
    private FrontAllocationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new FrontAllocationProperties();   // nodesPerDomain 默认 3
        allocator = new FrontNodeAllocator(nodeRepository, userFrontNodeRepository, properties);
    }

    /** 造一个前置节点：名字决定是否判为美国落地 */
    private ProxyNodeDto node(long id, String name, String failureDomain, NodeStatus status) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setId(id);
        dto.setName(name);
        dto.setRole(NodeRole.FRONT);
        dto.setFailureDomain(failureDomain);
        dto.setStatus(status);
        return dto;
    }

    @Test
    @DisplayName("两个故障域时各取 K 个，跨域分散")
    void takesKFromEachFailureDomain() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of(
                node(1, "🇺🇸[US]A1", "jp.tsdns.top", NodeStatus.ENABLED),
                node(2, "🇺🇸[US]A2", "jp.tsdns.top", NodeStatus.ENABLED),
                node(3, "🇺🇸[US]A3", "jp.tsdns.top", NodeStatus.ENABLED),
                node(4, "🇺🇸[US]A4", "jp.tsdns.top", NodeStatus.ENABLED),
                node(5, "🇺🇸[US]B1", "relay.other.net", NodeStatus.ENABLED),
                node(6, "🇺🇸[US]B2", "relay.other.net", NodeStatus.ENABLED)));

        FrontNodeAllocator.AllocationResult result = allocator.allocate(7L);

        assertThat(result.failureDomainCount()).isEqualTo(2);
        assertThat(result.nodeIds()).hasSize(5);          // 3 + 2（第二桶只有 2 个）
        assertThat(result.nodeIds()).contains(5L, 6L);    // 第二个故障域必须被取到
    }

    @Test
    @DisplayName("非美国落地的节点不参与分配——落地对来源做了国家级限制")
    void excludesNonUsNodes() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of(
                node(1, "🇺🇸[US]A1", "jp.tsdns.top", NodeStatus.ENABLED),
                node(2, "🇭🇰[HK]HongKong01", "hk.tsdns.top", NodeStatus.ENABLED)));

        assertThat(allocator.allocate(7L).nodeIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("故障域未解析（NULL）的节点不参与——不能保证它跨入口")
    void excludesNodesWithUnresolvedFailureDomain() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of(
                node(1, "🇺🇸[US]A1", "jp.tsdns.top", NodeStatus.ENABLED),
                node(2, "🇺🇸[US]A2", null, NodeStatus.ENABLED)));

        assertThat(allocator.allocate(7L).nodeIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("停用的节点不参与分配")
    void excludesDisabledNodes() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of(
                node(1, "🇺🇸[US]A1", "jp.tsdns.top", NodeStatus.ENABLED),
                node(2, "🇺🇸[US]A2", "jp.tsdns.top", NodeStatus.DISABLED)));

        assertThat(allocator.allocate(7L).nodeIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("只剩一个故障域时照常分配，但 failureDomainCount 如实报 1（这是采购信号）")
    void stillAllocatesWithSingleFailureDomain() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of(
                node(1, "🇺🇸[US]A1", "jp.tsdns.top", NodeStatus.ENABLED),
                node(2, "🇺🇸[US]A2", "jp.tsdns.top", NodeStatus.ENABLED)));

        FrontNodeAllocator.AllocationResult result = allocator.allocate(7L);

        assertThat(result.failureDomainCount()).isEqualTo(1);
        assertThat(result.nodeIds()).hasSize(2);
    }

    @Test
    @DisplayName("桶内按已分配用户数最少优先，摊平负载")
    void prefersLeastLoadedWithinDomain() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of(
                node(1, "🇺🇸[US]A1", "jp.tsdns.top", NodeStatus.ENABLED),
                node(2, "🇺🇸[US]A2", "jp.tsdns.top", NodeStatus.ENABLED),
                node(3, "🇺🇸[US]A3", "jp.tsdns.top", NodeStatus.ENABLED),
                node(4, "🇺🇸[US]A4", "jp.tsdns.top", NodeStatus.ENABLED)));
        // 1 号已被 9 个用户用，4 号 0 个 —— K=3 时该挑 4/3/2，不该挑到 1
        when(userFrontNodeRepository.countUsersByNodeId()).thenReturn(Map.of(1L, 9L, 2L, 1L, 3L, 1L));

        assertThat(allocator.allocate(7L).nodeIds()).doesNotContain(1L);
    }

    @Test
    @DisplayName("一个可用节点都没有时返回空结果，不抛")
    void returnsEmptyWhenNoCandidate() {
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of());

        FrontNodeAllocator.AllocationResult result = allocator.allocate(7L);

        assertThat(result.nodeIds()).isEmpty();
        assertThat(result.primaryNodeId()).isNull();
        assertThat(result.failureDomainCount()).isZero();
    }
}
