package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.service.AirportSubscriptionNodeSyncer.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅节点整体对齐：消失的删、新增的建、匹配上的原地更新")
class AirportSubscriptionNodeSyncerTest {

    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private FailureDomainResolver failureDomainResolver;
    private AirportSubscriptionNodeSyncer syncer;

    @BeforeEach
    void setUp() {
        when(nodeRepository.existsByName(anyString())).thenReturn(false);
        when(failureDomainResolver.resolve(anyString())).thenReturn("jp.tsdns.top");
        syncer = new AirportSubscriptionNodeSyncer(nodeRepository,
                new FailureDomainSyncer(failureDomainResolver, Clock.systemUTC()));
    }

    private static SubNode sub(String name, int port) {
        return new SubNode(name, "anytls", "us01a.example.com", port,
                Map.of("type", "anytls", "server", "us01a.example.com", "port", port, "password", "p"), false);
    }

    private static ProxyNodeDto existing(long id, String sourceName, int port) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setId(id);
        node.setName(sourceName);
        node.setRole(NodeRole.FRONT);
        node.setProtocol(NodeProtocol.MIHOMO);
        node.setServerAddr("us01a.example.com");
        node.setPort(port);
        node.setSourceName(sourceName);
        node.setAirportSubscriptionId(1L);
        return node;
    }

    @Test
    @DisplayName("selectRegionNodes：剔伪条目、按地区过滤、按 sourceName 去重且保序")
    void selectRegionNodes() {
        List<SubNode> parsed = List.of(
                new SubNode("剩余流量：1 GB", "anytls", "hk", 1, Map.of(), true),
                sub("🇭🇰[HK]HK-01", 1),
                sub("🇺🇸[US]US-02", 2),
                sub("🇺🇸[US]US-01", 3),
                sub("🇺🇸[US]US-02", 4));

        assertThat(syncer.selectRegionNodes(parsed, NodeRegion.US))
                .extracting(SubNode::sourceName)
                .containsExactly("🇺🇸[US]US-02", "🇺🇸[US]US-01");
    }

    @Test
    @DisplayName("库里 A、B，订阅里 B、C：删 A、更新 B、新建 C")
    void alignsToFetchedSet() {
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(
                existing(11L, "🇺🇸[US]A", 1000), existing(12L, "🇺🇸[US]B", 2000)));

        SyncResult result = syncer.sync(1L, List.of(sub("🇺🇸[US]B", 2001), sub("🇺🇸[US]C", 3000)),
                Map.of("us01a.example.com", "jp.tsdns.top"));

        assertThat(result.removed()).containsExactly("🇺🇸[US]A");
        assertThat(result.added()).containsExactly("🇺🇸[US]C");
        assertThat(result.updated()).isEqualTo(1);
        verify(nodeRepository).deleteById(11L);
        ArgumentCaptor<ProxyNodeDto> updated = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(updated.capture());
        assertThat(updated.getValue().getId()).isEqualTo(12L);
        assertThat(updated.getValue().getPort()).isEqualTo(2001);
        ArgumentCaptor<ProxyNodeDto> created = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).create(created.capture());
        assertThat(created.getValue().getSourceName()).isEqualTo("🇺🇸[US]C");
        assertThat(created.getValue().getRole()).isEqualTo(NodeRole.FRONT);
        assertThat(created.getValue().getProtocol()).isEqualTo(NodeProtocol.MIHOMO);
        assertThat(created.getValue().getFailureDomain()).isEqualTo("jp.tsdns.top");
    }

    @Test
    @DisplayName("匹配上的节点只更新参数，不动管理员改过的名称与备注")
    void keepsAdminEditedNameAndRemark() {
        ProxyNodeDto node = existing(12L, "🇺🇸[US]B", 2000);
        node.setName("我改过的名字");
        node.setRemark("备注");
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(node));

        syncer.sync(1L, List.of(sub("🇺🇸[US]B", 2001)), Map.of());

        ArgumentCaptor<ProxyNodeDto> updated = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(updated.capture());
        assertThat(updated.getValue().getName()).isEqualTo("我改过的名字");
        assertThat(updated.getValue().getRemark()).isEqualTo("备注");
        verify(nodeRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("新建撞全局唯一名时加 (2) 后缀")
    void suffixesDuplicateName() {
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of());
        when(nodeRepository.existsByName("🇺🇸[US]C")).thenReturn(true);

        syncer.sync(1L, List.of(sub("🇺🇸[US]C", 3000)), Map.of());

        ArgumentCaptor<ProxyNodeDto> created = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).create(created.capture());
        assertThat(created.getValue().getName()).isEqualTo("🇺🇸[US]C (2)");
    }
}
