package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.Airport;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.AirportSubscriptionCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅导入：把节点的故障域一并解析入库")
class AdminAirportSubscriptionServiceImplTest {

    @Mock private AirportSubscriptionRepository airportSubscriptionRepository;
    @Mock private AirportRepository airportRepository;
    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserFrontNodeRepository userFrontNodeRepository;
    @Mock private SubFetchClient subFetchClient;
    @Mock private FailureDomainResolver failureDomainResolver;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TrafficAlertService trafficAlertService;

    private AdminAirportSubscriptionServiceImpl service;

    private static final String SUB_YAML = """
            proxies:
              - { name: '🇺🇸[US]01', type: anytls, server: hk01a.t11-a.app, port: 35660, password: p }
            """;

    @BeforeEach
    void setUp() {
        // 事务模板在单测里退化成「立即执行回调」，不引入真事务管理器
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        doAnswer(inv -> {
            inv.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(SUB_YAML, null, null, null, null));
        when(nodeRepository.existsByName(anyString())).thenReturn(false);

        service = new AdminAirportSubscriptionServiceImpl(airportSubscriptionRepository, airportRepository, nodeRepository, userRepository,
                userFrontNodeRepository,
                subFetchClient, new SubYamlParser(), transactionTemplate,
                // syncer 用真实实现、只把最底层的 DNS 解析口替换成假的：
                // 「按 serverAddr 去重」「跳过伪条目」这些口径正是本类要守的行为，不该被 mock 掉
                new FailureDomainSyncer(failureDomainResolver, Clock.systemUTC()), trafficAlertService);
    }

    private AirportSubscriptionDto group(long id) {
        AirportSubscriptionDto group = new AirportSubscriptionDto();
        group.setId(id);
        group.setName("候选机场");
        group.setSubUrl("https://example.com/sub?token=x");
        return group;
    }

    @Test
    @DisplayName("新建节点时写入解析到的故障域与解析时间")
    void writesFailureDomainOnCreate() {
        when(airportSubscriptionRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByAirportSubscriptionIdAndSourceName(1L, "🇺🇸[US]01")).thenReturn(Optional.empty());
        when(failureDomainResolver.resolve("hk01a.t11-a.app")).thenReturn("hk.tsdns.top");

        service.importNodes(1L);

        ArgumentCaptor<ProxyNodeDto> captor = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).create(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("hk.tsdns.top");
        assertThat(captor.getValue().getFailureDomainCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("解析失败时保留原有故障域，且不让导入失败")
    void keepsPreviousFailureDomainWhenResolveFails() {
        ProxyNodeDto existing = new ProxyNodeDto();
        existing.setId(7L);
        existing.setSourceName("🇺🇸[US]01");
        existing.setFailureDomain("jp.tsdns.top");
        existing.setFailureDomainCheckedAt(Instant.parse("2026-09-01T00:00:00Z"));

        when(airportSubscriptionRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByAirportSubscriptionIdAndSourceName(1L, "🇺🇸[US]01")).thenReturn(Optional.of(existing));
        when(failureDomainResolver.resolve(anyString())).thenReturn(null);

        assertThatCode(() -> service.importNodes(1L)).doesNotThrowAnyException();

        ArgumentCaptor<ProxyNodeDto> captor = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("jp.tsdns.top");
        assertThat(captor.getValue().getFailureDomainCheckedAt())
                .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("同一域名的多个节点只解析一次，不重复查 DNS")
    void resolvesEachServerAddrOnce() {
        when(airportSubscriptionRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByAirportSubscriptionIdAndSourceName(anyLong(), anyString())).thenReturn(Optional.empty());
        when(failureDomainResolver.resolve(anyString())).thenReturn("hk.tsdns.top");
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: '🇺🇸[US]01', type: anytls, server: hk01a.t11-a.app, port: 35660, password: p }
                  - { name: '🇺🇸[US]02', type: anytls, server: hk01a.t11-a.app, port: 35661, password: p }
                """, null, null, null, null));

        service.importNodes(1L);

        verify(failureDomainResolver, times(1)).resolve("hk01a.t11-a.app");
    }

    @Test
    @DisplayName("新建订阅时额度已跨档：当场推送告警，且传给告警服务的订阅带着真实自增 id（不是 null）")
    void alertsOnCreateWhenQuotaAlreadyCrossed() {
        Airport airport = new Airport();
        airport.setId(1L);
        airport.setName("泰山云");
        when(airportRepository.findById(1L)).thenReturn(Optional.of(airport));
        when(airportSubscriptionRepository.create(any())).thenReturn(42L);
        when(nodeRepository.findByAirportSubscriptionIdAndSourceName(anyLong(), anyString())).thenReturn(Optional.empty());
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(SUB_YAML, null, 95L, 100L, null));

        AirportSubscriptionCreateRequest request = new AirportSubscriptionCreateRequest();
        request.setName("新机场");
        request.setAirportId(1L);
        request.setAccount("a@x.com");
        request.setBandwidthMbps(300);
        request.setSubUrl("https://example.com/sub?token=y");

        Long airportSubscriptionId = service.create(request);

        assertThat(airportSubscriptionId).isEqualTo(42L);
        ArgumentCaptor<AirportSubscriptionDto> captor = ArgumentCaptor.forClass(AirportSubscriptionDto.class);
        verify(trafficAlertService).checkAndNotify(captor.capture(), any());
        // airportSubscriptionRepository.create 不会把自增主键回写到传入的 DTO 上；这里锁住「调用前必须手动补上 id」
        // 这一步，漏了的话 TrafficAlertService 内部的 airportSubscriptionRepository.update(group) 会因 id 为 null
        // 而更新不到任何行，档位悄悄丢失且没有任何报错
        assertThat(captor.getValue().getId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("只导入美国节点，也只为它们解析故障域：订阅里几十个节点、只导入其中几个时，不为其余的白查一次 DNS")
    void resolvesOnlyUsNodes() {
        when(airportSubscriptionRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByAirportSubscriptionIdAndSourceName(anyLong(), anyString())).thenReturn(Optional.empty());
        when(failureDomainResolver.resolve(anyString())).thenReturn("hk.tsdns.top");
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: '🇺🇸[US]01', type: anytls, server: us01a.t11-a.app, port: 35660, password: p }
                  - { name: '🇭🇰[HK]99', type: anytls, server: hk99a.t11-a.app, port: 35355, password: p }
                """, null, null, null, null));

        service.importNodes(1L);

        verify(failureDomainResolver).resolve("us01a.t11-a.app");
        verify(failureDomainResolver, never()).resolve("hk99a.t11-a.app");
        verify(nodeRepository, times(1)).create(any());
    }

    @Test
    @DisplayName("机场塞的伪条目不导入也不查 DNS：它不是节点，server 字段也不是真实中转入口")
    void skipsSuspectedInfoEntries() {
        when(airportSubscriptionRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByAirportSubscriptionIdAndSourceName(anyLong(), anyString())).thenReturn(Optional.empty());
        when(failureDomainResolver.resolve(anyString())).thenReturn("hk.tsdns.top");
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: '剩余流量：18.2 GB', type: anytls, server: info.t11-a.app, port: 1, password: p }
                  - { name: '🇺🇸[US]01', type: anytls, server: us01a.t11-a.app, port: 35660, password: p }
                """, null, null, null, null));

        service.importNodes(1L);

        verify(failureDomainResolver, never()).resolve("info.t11-a.app");
        verify(failureDomainResolver).resolve("us01a.t11-a.app");
        verify(nodeRepository, times(1)).create(any());
    }
}
