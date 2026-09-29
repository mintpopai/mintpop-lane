package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.response.SubAuditResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.NodeRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
              - { name: '【US】San Jose03', type: anytls, server: us03a.example.com, port: 35663, password: p }
            """;

    @Mock private SubFetchClient subFetchClient;
    @Mock private FailureDomainResolver failureDomainResolver;
    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private AirportSubscriptionRepository airportSubscriptionRepository;

    private SubAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        when(subFetchClient.fetch(SUB_URL))
                .thenReturn(new SubFetchResult(SUB_YAML, "TaiShan Net", 1L, 100L,
                        Instant.parse("2027-05-02T08:04:00Z")));
        when(failureDomainResolver.resolve(anyString())).thenReturn("candidate.example.net");
        when(nodeRepository.findAll(NodeRole.FRONT)).thenReturn(List.of());
        SystemSettingService systemSettingService = mock(SystemSettingService.class);
        when(systemSettingService.frontSettings()).thenReturn(new FrontSettings(NodeRegion.US, 3, 20));
        service = new SubAuditServiceImpl(subFetchClient, new SubYamlParser(),
                // syncer 用真实实现、只替换最底层的 DNS 解析口：本类要守的正是「按 serverAddr 去重」
                new FailureDomainSyncer(failureDomainResolver, Clock.systemUTC()),
                nodeRepository, airportSubscriptionRepository, systemSettingService);
    }

    /** 造一个库里已存在、且属于指定订阅的前置节点 */
    private ProxyNodeDto existingNode(String failureDomain, Long airportSubscriptionId) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setId(1L);
        node.setRole(NodeRole.FRONT);
        node.setFailureDomain(failureDomain);
        node.setAirportSubscriptionId(airportSubscriptionId);
        return node;
    }

    @Test
    @DisplayName("与库中已有节点撞故障域时，conflictsWith 列出订阅名")
    void reportsConflictWithExistingGroups() {
        when(nodeRepository.findAll(NodeRole.FRONT))
                .thenReturn(List.of(existingNode("candidate.example.net", 9L)));
        AirportSubscriptionDto existing = new AirportSubscriptionDto();
        existing.setId(9L);
        existing.setName("TaiShan Net");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(existing));

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
        assertThat(report.usNodeNames()).containsExactly("🇺🇸[US]San Jose07", "【US】San Jose03");
        assertThat(report.usNodeCount()).isEqualTo(2);
        assertThat(report.totalNodes()).isEqualTo(3);
    }

    @Test
    @DisplayName("机场名直接取自 SubFetchResult.airportName（由拉取层从 content-disposition 解出）")
    void carriesAirportNameFromFetchResult() {
        assertThat(service.audit(SUB_URL).airportName()).isEqualTo("TaiShan Net");
    }

    @Test
    @DisplayName("尽调全程不写库")
    void neverPersists() {
        service.audit(SUB_URL);
        verify(nodeRepository, never()).create(any());
        verify(nodeRepository, never()).update(any());
        verify(airportSubscriptionRepository, never()).create(any());
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

    // —— 外呼扇出的上限 ——

    @Test
    @DisplayName("同一 serverAddr 只查一次 CNAME：81 个节点 2 个故障域时不该查 81 次")
    void resolvesEachServerAddrOnce() {
        // 同一台中转机在订阅里以多个端口出现、域名却是同一个——这正是现役订阅的形态
        when(subFetchClient.fetch(SUB_URL)).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: '🇺🇸[US]San Jose07', type: anytls, server: us07a.example.com, port: 35668, password: p }
                  - { name: '🇺🇸[US]San Jose08', type: anytls, server: us07a.example.com, port: 35669, password: p }
                  - { name: '🇺🇸[US]San Jose09', type: anytls, server: us07a.example.com, port: 35670, password: p }
                """, "TaiShan Net", null, null, null));

        SubAuditResponse report = service.audit(SUB_URL);

        verify(failureDomainResolver, times(1)).resolve("us07a.example.com");
        assertThat(report.failureDomains()).singleElement()
                .extracting(SubAuditResponse.FailureDomainReport::nodeCount).isEqualTo(3);
    }

    @Test
    @DisplayName("机场塞的伪条目不算节点：totalNodes 与故障域的 nodeCount 都不含它")
    void excludesSuspectedInfoEntriesFromCounts() {
        when(subFetchClient.fetch(SUB_URL)).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: '剩余流量：18.2 GB', type: anytls, server: info.example.com, port: 1, password: p }
                  - { name: '到期时间：2027-05-02', type: anytls, server: info.example.com, port: 2, password: p }
                  - { name: '🇺🇸[US]San Jose07', type: anytls, server: us07a.example.com, port: 35668, password: p }
                """, "TaiShan Net", null, null, null));

        SubAuditResponse report = service.audit(SUB_URL);

        assertThat(report.totalNodes()).isEqualTo(1);
        assertThat(report.failureDomains()).singleElement()
                .extracting(SubAuditResponse.FailureDomainReport::nodeCount).isEqualTo(1);
        verify(failureDomainResolver, never()).resolve("info.example.com");
    }
}
