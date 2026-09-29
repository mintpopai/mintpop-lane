package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.service.SubRefreshService.RefreshOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅 5 分钟刷新：整体对齐节点，拉取失败不动节点只记状态并推飞书")
class SubRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-29T02:00:00Z");
    private static final String YAML_ONE_NODE =
            "proxies:\n  - { name: '🇺🇸[US]US-01', type: anytls, server: us01a.example.com, port: 35660, password: p }";
    private static final String YAML_NO_US =
            "proxies:\n  - { name: '🇭🇰[HK]HK-01', type: anytls, server: hk01a.example.com, port: 35660, password: p }";

    @Mock private AirportSubscriptionRepository airportSubscriptionRepository;
    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private SubFetchClient subFetchClient;
    @Mock private FailureDomainResolver failureDomainResolver;
    @Mock private NodeNotifyService nodeNotifyService;
    @Mock private TrafficAlertService trafficAlertService;
    @Mock private SystemSettingService systemSettingService;
    private SubRefreshService service;

    @BeforeEach
    void setUp() {
        when(failureDomainResolver.resolve(anyString())).thenReturn("jp.tsdns.top");
        when(systemSettingService.frontSettings()).thenReturn(new FrontSettings(NodeRegion.US, 3, 20));
        when(nodeRepository.existsByName(anyString())).thenReturn(false);
        // 事务模板用「直接执行回调」的桩：单测不起数据库
        TransactionTemplate tx = mock(TransactionTemplate.class);
        org.mockito.Mockito.doAnswer(inv -> {
            inv.<java.util.function.Consumer<org.springframework.transaction.TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        FailureDomainSyncer failureDomainSyncer = new FailureDomainSyncer(failureDomainResolver, Clock.systemUTC());
        service = new SubRefreshService(airportSubscriptionRepository, subFetchClient, new SubYamlParser(),
                failureDomainSyncer, new AirportSubscriptionNodeSyncer(nodeRepository, failureDomainSyncer, mock(SubscriptionRenderCache.class)),
                nodeNotifyService, trafficAlertService, systemSettingService, tx, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AirportSubscriptionDto group(long id, String name) {
        AirportSubscriptionDto g = new AirportSubscriptionDto();
        g.setId(id);
        g.setName(name);
        g.setSubUrl("https://example.com/sub?token=x");
        return g;
    }

    private static ProxyNodeDto node(long id, String sourceName, int port) {
        ProxyNodeDto n = new ProxyNodeDto();
        n.setId(id);
        n.setName(sourceName);
        n.setRole(NodeRole.FRONT);
        n.setProtocol(NodeProtocol.MIHOMO);
        n.setServerAddr("us01a.example.com");
        n.setPort(port);
        n.setSourceName(sourceName);
        n.setAirportSubscriptionId(1L);
        return n;
    }

    @Test
    @DisplayName("成功：匹配的节点更新、消失的删除、新增的建；清空失败状态；fetchedAt 用注入的 Clock")
    void alignsNodesAndClearsFailure() {
        AirportSubscriptionDto g = group(1L, "A 家");
        g.setFetchFailedSince(Instant.parse("2026-09-29T01:00:00Z"));
        g.setLastFetchError("旧错误");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(
                node(7L, "🇺🇸[US]US-01", 35555), node(8L, "🇺🇸[US]US-09", 1)));

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).isEmpty();
        ArgumentCaptor<ProxyNodeDto> updated = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(updated.capture());
        assertThat(updated.getValue().getPort()).isEqualTo(35660);
        verify(nodeRepository).deleteById(8L);
        assertThat(g.getFetchFailedSince()).isNull();
        assertThat(g.getLastFetchError()).isNull();
        assertThat(g.getFetchedAt()).isEqualTo(NOW);
        verify(airportSubscriptionRepository).update(g);
        verify(nodeNotifyService, never()).notifySubFetchFailed(any(), anyString(), any());
    }

    @Test
    @DisplayName("拉取失败：节点原样不动，写 fetchFailedSince（首次）与 lastFetchError，推飞书，进失败名单")
    void fetchFailureLeavesNodesUntouchedAndMarksSubscription() {
        AirportSubscriptionDto g = group(1L, "A 家");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenThrow(new BizException(BizCodeEnum.SUB_FETCH_FAILED));

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).containsExactly("A 家");
        verify(nodeRepository, never()).findByAirportSubscriptionId(any());
        verify(nodeRepository, never()).deleteById(any());
        verify(nodeRepository, never()).update(any());
        assertThat(g.getFetchFailedSince()).isEqualTo(NOW);
        assertThat(g.getLastFetchError()).isEqualTo(BizCodeEnum.SUB_FETCH_FAILED.getMessage());
        verify(airportSubscriptionRepository).update(g);
        verify(nodeNotifyService).notifySubFetchFailed(eq(g), eq(BizCodeEnum.SUB_FETCH_FAILED.getMessage()), eq(NOW));
    }

    @Test
    @DisplayName("连续失败：fetchFailedSince 保留首次时间，每轮仍推飞书（不去重）")
    void repeatedFailureKeepsFirstTimestampAndNotifiesEveryRound() {
        AirportSubscriptionDto g = group(1L, "A 家");
        Instant first = Instant.parse("2026-09-29T01:00:00Z");
        g.setFetchFailedSince(first);
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenThrow(new BizException(BizCodeEnum.SUB_FETCH_FAILED));

        service.refreshAllNow();

        assertThat(g.getFetchFailedSince()).isEqualTo(first);
        verify(nodeNotifyService).notifySubFetchFailed(eq(g), anyString(), eq(first));
    }

    @Test
    @DisplayName("解析成功但当前地区一个节点都没有：按失败处理，不删节点")
    void emptyRegionSetIsTreatedAsFailure() {
        AirportSubscriptionDto g = group(1L, "A 家");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_NO_US, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(node(7L, "🇺🇸[US]US-01", 35660)));

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).containsExactly("A 家");
        verify(nodeRepository, never()).deleteById(any());
        verify(nodeRepository, never()).update(any());
        verify(nodeRepository, never()).create(any());
        assertThat(g.getLastFetchError()).isEqualTo(BizCodeEnum.SUB_NO_REGION_NODES.getMessage());
        verify(nodeNotifyService).notifySubFetchFailed(eq(g), eq(BizCodeEnum.SUB_NO_REGION_NODES.getMessage()), eq(NOW));
    }

    @Test
    @DisplayName("一个订阅失败不影响其它订阅继续刷新")
    void oneFailureDoesNotStopOthers() {
        AirportSubscriptionDto bad = group(1L, "坏");
        AirportSubscriptionDto good = group(2L, "好");
        good.setSubUrl("https://example.com/good");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(bad, good));
        when(subFetchClient.fetch("https://example.com/sub?token=x")).thenThrow(new BizException(BizCodeEnum.SUB_FETCH_FAILED));
        when(subFetchClient.fetch("https://example.com/good")).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(2L)).thenReturn(List.of());

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).containsExactly("坏");
        verify(nodeRepository).create(any());
    }

    @Test
    @DisplayName("事务内对齐抛异常：首次失败时间保留旧值，fetchedAt 不被记成成功")
    void syncFailureDoesNotResetFirstFailureOrFakeFetchedAt() {
        AirportSubscriptionDto g = group(1L, "A 家");
        Instant first = Instant.parse("2026-09-29T01:00:00Z");
        Instant oldFetchedAt = Instant.parse("2026-09-28T00:00:00Z");
        g.setFetchFailedSince(first);
        g.setFetchedAt(oldFetchedAt);
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenThrow(new RuntimeException("db"));

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).containsExactly("A 家");
        assertThat(g.getFetchFailedSince()).isEqualTo(first);
        assertThat(g.getFetchedAt()).isEqualTo(oldFetchedAt);
        assertThat(g.getLastFetchError()).isNotNull();
    }

    @Test
    @DisplayName("事务内对齐抛异常且此前无失败记录：首次失败时间记为本轮")
    void syncFailureSetsFirstFailureWhenNoneBefore() {
        AirportSubscriptionDto g = group(1L, "A 家");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenThrow(new RuntimeException("db"));

        service.refreshAllNow();

        assertThat(g.getFetchFailedSince()).isEqualTo(NOW);
        assertThat(g.getFetchedAt()).isNull();
    }

    @Test
    @DisplayName("额度告警检查抛异常：不算拉取失败，不标记订阅")
    void trafficAlertFailureIsNotFetchFailure() {
        AirportSubscriptionDto g = group(1L, "A 家");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(g));
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("notify")).when(trafficAlertService).checkAndNotify(any(), any());

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).isEmpty();
        assertThat(g.getFetchFailedSince()).isNull();
        verify(nodeNotifyService, never()).notifySubFetchFailed(any(), anyString(), any());
    }

    @Test
    @DisplayName("标记失败本身抛异常：仍进失败名单，且后面的订阅照常刷新")
    void markFailedThrowingDoesNotStopLoop() {
        AirportSubscriptionDto bad = group(1L, "坏");
        AirportSubscriptionDto good = group(2L, "好");
        good.setSubUrl("https://example.com/good");
        when(airportSubscriptionRepository.findAll()).thenReturn(List.of(bad, good));
        when(subFetchClient.fetch("https://example.com/sub?token=x")).thenThrow(new BizException(BizCodeEnum.SUB_FETCH_FAILED));
        when(subFetchClient.fetch("https://example.com/good")).thenReturn(new SubFetchResult(YAML_ONE_NODE, null, null, null, null));
        when(nodeRepository.findByAirportSubscriptionId(2L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new RuntimeException("db")).when(airportSubscriptionRepository).update(bad);

        RefreshOutcome outcome = service.refreshAllNow();

        assertThat(outcome.failedSubscriptionNames()).containsExactly("坏");
        verify(nodeRepository).create(any());
    }
}
