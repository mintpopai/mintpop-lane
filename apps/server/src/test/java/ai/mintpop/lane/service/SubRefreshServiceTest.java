package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订阅定时刷新：只盯「已有节点原地对齐、增删只告警不落库、端点变化单独告警、单分组失败不中断整轮」。
 * 拉取、YAML 解析、故障域解析与通知都替换成假的（除 SubYamlParser 用真实实现，纯内存计算不出网）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅定时刷新：只对齐已有节点，增删只告警")
class SubRefreshServiceTest {

    @Mock
    private NodeGroupRepository groupRepository;
    @Mock
    private ProxyNodeRepository nodeRepository;
    @Mock
    private SubFetchClient subFetchClient;
    @Mock
    private FailureDomainResolver failureDomainResolver;
    @Mock
    private NodeNotifyService nodeNotifyService;
    @Mock
    private TrafficAlertService trafficAlertService;

    private SubRefreshService service;

    private static final String YAML_ONE_NODE = """
            proxies:
              - { name: 'US-01', type: anytls, server: us01a.example.com, port: 35660, password: p }
            """;

    @BeforeEach
    void setUp() {
        when(failureDomainResolver.resolve(anyString())).thenReturn("jp.tsdns.top");
        service = new SubRefreshService(groupRepository, nodeRepository, subFetchClient,
                // syncer 用真实实现、只替换最底层的 DNS 解析口（理由同 AdminNodeGroupServiceImplTest）
                new SubYamlParser(), new FailureDomainSyncer(failureDomainResolver, Clock.systemUTC()),
                nodeNotifyService, trafficAlertService);
    }

    private NodeGroupDto group(long id, String name) {
        NodeGroupDto group = new NodeGroupDto();
        group.setId(id);
        group.setName(name);
        group.setSubUrl("https://example.com/sub?token=x");
        return group;
    }

    private ProxyNodeDto node(long id, String sourceName, int port) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setId(id);
        node.setSourceName(sourceName);
        node.setServerAddr("us01a.example.com");
        node.setPort(port);
        return node;
    }

    @Test
    @DisplayName("已存在的节点原地更新参数、端口与故障域")
    void updatesExistingNodes() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(node(7L, "US-01", 35555)));

        service.refreshAll();

        ArgumentCaptor<ProxyNodeDto> captor = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(captor.capture());
        assertThat(captor.getValue().getPort()).isEqualTo(35660);
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("jp.tsdns.top");
    }

    @Test
    @DisplayName("订阅里新增的节点不自动入库，只推飞书告知")
    void doesNotAutoAddNewNodes() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of());

        service.refreshAll();

        verify(nodeRepository, never()).create(any());
        verify(nodeNotifyService).notifySubNodesChanged(any(), eq(List.of("US-01")), eq(List.of()));
    }

    @Test
    @DisplayName("订阅里消失的节点不自动删除，只推飞书告知")
    void doesNotAutoDeleteMissingNodes() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByGroupId(1L))
                .thenReturn(List.of(node(7L, "US-01", 35660), node(8L, "US-99", 35699)));

        service.refreshAll();

        verify(nodeRepository, never()).deleteById(anyLong());
        verify(nodeNotifyService).notifySubNodesChanged(any(), eq(List.of()), eq(List.of("US-99")));
    }

    @Test
    @DisplayName("节点端口变了要推飞书——此前下发给用户的配置已经失效")
    void notifiesOnPortChange() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(node(7L, "US-01", 35555)));

        service.refreshAll();

        verify(nodeNotifyService).notifyNodeEndpointChanged(any(), eq("us01a.example.com:35555"),
                eq("us01a.example.com:35660"));
    }

    @Test
    @DisplayName("端口未变不推端点变更告警")
    void doesNotNotifyWhenEndpointUnchanged() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(node(7L, "US-01", 35660)));

        service.refreshAll();

        verify(nodeNotifyService, never()).notifyNodeEndpointChanged(any(), any(), any());
    }

    @Test
    @DisplayName("单个分组失败不中断整轮")
    void oneGroupFailureDoesNotStopTheRound() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "坏的"), group(2L, "好的")));
        when(subFetchClient.fetch(anyString()))
                .thenThrow(new BizException(BizCodeEnum.SUB_FETCH_FAILED))
                .thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByGroupId(2L)).thenReturn(List.of(node(7L, "US-01", 35555)));

        assertThatCode(() -> service.refreshAll()).doesNotThrowAnyException();

        verify(nodeRepository).update(any());
    }

    @Test
    @DisplayName("解析故障域失败时保留节点原有故障域，不抹成 null")
    void keepsOriginalFailureDomainWhenResolutionFails() {
        when(failureDomainResolver.resolve(anyString())).thenReturn(null);
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        ProxyNodeDto existing = node(7L, "US-01", 35555);
        existing.setFailureDomain("old.tsdns.top");
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(existing));

        service.refreshAll();

        ArgumentCaptor<ProxyNodeDto> captor = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("old.tsdns.top");
    }

    @Test
    @DisplayName("每轮都把本次拉取的额度信息与拉取时间写回分组")
    void persistsFetchedTrafficInfoOnGroup() {
        NodeGroupDto group = group(1L, "A 家");
        when(groupRepository.findAll()).thenReturn(List.of(group));
        when(subFetchClient.fetch(anyString()))
                .thenReturn(new SubFetchResult(YAML_ONE_NODE, "机场名", 100L, 1000L, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(node(7L, "US-01", 35660)));

        service.refreshAll();

        verify(groupRepository).update(group);
        assertThat(group.getUsedBytes()).isEqualTo(100L);
        assertThat(group.getTotalBytes()).isEqualTo(1000L);
        assertThat(group.getFetchedAt()).isNotNull();
    }

    @Test
    @DisplayName("订阅里同名节点出现两次时只对齐一次——与 added 那一侧的去重口径对称")
    void deduplicatesExistingNodesBySourceName() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: 'US-01', type: anytls, server: us01a.example.com, port: 35660, password: p }
                  - { name: 'US-01', type: anytls, server: us01a.example.com, port: 35661, password: p }
                """, null, null, null, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(node(7L, "US-01", 35555)));

        service.refreshAll();

        // 不去重的话同一个节点会被更新两遍，端点变更告警也会跟着重复推
        verify(nodeRepository, times(1)).update(any());
        verify(nodeNotifyService, times(1)).notifyNodeEndpointChanged(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("伪条目不查 DNS：刷新时同样走 FailureDomainSyncer 的统一口径")
    void skipsSuspectedInfoEntriesOnRefresh() {
        when(groupRepository.findAll()).thenReturn(List.of(group(1L, "A 家")));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: '到期时间：2027-05-02', type: anytls, server: info.example.com, port: 1, password: p }
                  - { name: 'US-01', type: anytls, server: us01a.example.com, port: 35660, password: p }
                """, null, null, null, null));
        when(nodeRepository.findByGroupId(1L)).thenReturn(List.of(node(7L, "US-01", 35660)));

        service.refreshAll();

        verify(failureDomainResolver, never()).resolve("info.example.com");
        verify(failureDomainResolver).resolve("us01a.example.com");
    }
}
