package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EcsDnsClient;
import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.config.EntryIpWatchProperties;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.response.SubAuditResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅尽调：出采购决策报告，只读不落库")
class SubAuditServiceImplTest {

    private static final String SUB_URL = "https://example.com/sub?token=x";
    private static final String SUB_YAML = """
            proxies:
              - { name: '🇺🇸[US]San Jose07', type: anytls, server: us07a.example.com, port: 35668, password: p }
              - { name: '🇭🇰[HK]HongKong01', type: anytls, server: hk01a.example.com, port: 35355, password: p }
              - { name: 'United States 03', type: anytls, server: us03a.example.com, port: 35663, password: p }
            """;

    @Mock private SubFetchClient subFetchClient;
    @Mock private FailureDomainResolver failureDomainResolver;
    @Mock private EcsDnsClient ecsDnsClient;
    @Mock private IpAsnClient ipAsnClient;
    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private NodeGroupRepository groupRepository;

    private SubAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        when(subFetchClient.fetch(SUB_URL))
                .thenReturn(new SubFetchResult(SUB_YAML, 1L, 100L, Instant.parse("2027-05-02T08:04:00Z")));
        when(failureDomainResolver.resolve(anyString())).thenReturn("candidate.example.net");
        when(ecsDnsClient.resolveA(anyString(), anyString())).thenReturn(List.of("203.0.113.1"));
        when(ipAsnClient.lookupAsn(anyString())).thenReturn(java.util.Optional.of("AS16509"));
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of());
        service = new SubAuditServiceImpl(subFetchClient, new SubYamlParser(), failureDomainResolver,
                ecsDnsClient, ipAsnClient, nodeRepository, groupRepository, new EntryIpWatchProperties());
    }

    /** 造一个库里已存在、且属于指定分组的前置节点 */
    private ProxyNodeDto existingNode(String failureDomain, Long groupId) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setId(1L);
        node.setRole(NodeRole.FRONT);
        node.setFailureDomain(failureDomain);
        node.setGroupId(groupId);
        return node;
    }

    @Test
    @DisplayName("与库中已有节点撞故障域时，conflictsWith 列出分组名")
    void reportsConflictWithExistingGroups() {
        when(nodeRepository.findAll(NodeRole.FRONT))
                .thenReturn(List.of(existingNode("candidate.example.net", 9L)));
        NodeGroupDto existing = new NodeGroupDto();
        existing.setId(9L);
        existing.setName("TaiShan Net");
        when(groupRepository.findAll()).thenReturn(List.of(existing));

        assertThat(service.audit(SUB_URL).conflictsWith()).containsExactly("TaiShan Net");
    }

    @Test
    @DisplayName("不撞故障域时 conflictsWith 为空")
    void reportsNoConflict() {
        when(nodeRepository.findAll(NodeRole.FRONT))
                .thenReturn(List.of(existingNode("jp.tsdns.top", 9L)));

        assertThat(service.audit(SUB_URL).conflictsWith()).isEmpty();
    }

    @Test
    @DisplayName("按节点名启发式识别美国落地节点，并把判定结果原样列出供人核对")
    void identifiesUsNodesByName() {
        SubAuditResponse report = service.audit(SUB_URL);
        assertThat(report.usNodeNames()).containsExactly("🇺🇸[US]San Jose07", "United States 03");
        assertThat(report.usNodeCount()).isEqualTo(2);
        assertThat(report.totalNodes()).isEqualTo(3);
    }

    @Test
    @DisplayName("尽调全程不写库")
    void neverPersists() {
        service.audit(SUB_URL);
        verify(nodeRepository, never()).create(any());
        verify(nodeRepository, never()).update(any());
        verify(groupRepository, never()).create(any());
    }

    @Test
    @DisplayName("拉取失败时抛 SUB_FETCH_FAILED，不另造错误码")
    void propagatesFetchFailure() {
        when(subFetchClient.fetch(anyString())).thenThrow(new BizException(BizCodeEnum.SUB_FETCH_FAILED));
        assertThatThrownBy(() -> service.audit("https://bad.example.com/sub"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }
}
