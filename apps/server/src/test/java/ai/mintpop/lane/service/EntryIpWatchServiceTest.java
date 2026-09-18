package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EcsDnsClient;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.config.EntryIpWatchProperties;
import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.repository.EntryIpHistoryRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("入口 IP 巡检：变了才落库并通知")
class EntryIpWatchServiceTest {

    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private EntryIpHistoryRepository historyRepository;
    @Mock private EcsDnsClient ecsDnsClient;
    @Mock private IpAsnClient ipAsnClient;
    @Mock private NodeNotifyService nodeNotifyService;

    private EntryIpWatchService service;
    private EntryIpWatchProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EntryIpWatchProperties();
        // 只留一个视角，让断言不被四倍放大
        properties.setVantages(Map.of(DnsVantage.OVERSEAS, "104.16.0.0/24"));
        when(nodeRepository.findDistinctFailureDomains()).thenReturn(List.of("jp.tsdns.top"));
        service = new EntryIpWatchService(nodeRepository, historyRepository, ecsDnsClient, ipAsnClient,
                nodeNotifyService, properties);
    }

    private void latestIs(String ips) {
        EntryIpHistory latest = new EntryIpHistory();
        latest.setFailureDomain("jp.tsdns.top");
        latest.setVantage(DnsVantage.OVERSEAS);
        latest.setEntryIps(ips);
        when(historyRepository.findLatest("jp.tsdns.top", DnsVantage.OVERSEAS))
                .thenReturn(Optional.of(latest));
    }

    @Test
    @DisplayName("入口 IP 与上次不同即落库并推飞书")
    void recordsAndNotifiesOnChange() {
        latestIs("13.192.233.178");
        when(ecsDnsClient.resolveA("jp.tsdns.top", "104.16.0.0/24")).thenReturn(List.of("34.84.255.241"));

        service.watchAll();

        ArgumentCaptor<EntryIpHistory> captor = ArgumentCaptor.forClass(EntryIpHistory.class);
        verify(historyRepository).create(captor.capture());
        assertThat(captor.getValue().getEntryIps()).isEqualTo("34.84.255.241");
        verify(nodeNotifyService).notifyEntryIpChanged("jp.tsdns.top", DnsVantage.OVERSEAS,
                "13.192.233.178", "34.84.255.241");
    }

    @Test
    @DisplayName("入口 IP 未变则不落库、不通知")
    void staysQuietWhenUnchanged() {
        latestIs("13.192.233.178");
        when(ecsDnsClient.resolveA(anyString(), anyString())).thenReturn(List.of("13.192.233.178"));

        service.watchAll();

        verify(historyRepository, never()).create(any());
        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("首次观测直接落一条基线，但不当作变更去告警")
    void recordsBaselineWithoutAlerting() {
        when(historyRepository.findLatest(anyString(), any())).thenReturn(Optional.empty());
        when(ecsDnsClient.resolveA(anyString(), anyString())).thenReturn(List.of("13.192.233.178"));

        service.watchAll();

        verify(historyRepository).create(any());
        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("解析失败不写入、不清空历史，下轮再试")
    void skipsOnResolveFailure() {
        latestIs("13.192.233.178");
        when(ecsDnsClient.resolveA(anyString(), anyString())).thenReturn(List.of());

        service.watchAll();

        verify(historyRepository, never()).create(any());
        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("同一故障域的多个 IP 按字典序归一后比对，顺序变化不误报")
    void normalizesIpOrderBeforeComparing() {
        latestIs("13.192.233.178,13.196.205.245");
        when(ecsDnsClient.resolveA(anyString(), anyString()))
                .thenReturn(List.of("13.196.205.245", "13.192.233.178"));

        service.watchAll();

        verify(historyRepository, never()).create(any());
    }

    @Test
    @DisplayName("单个故障域失败不中断整轮")
    void oneDomainFailureDoesNotStopTheRound() {
        when(nodeRepository.findDistinctFailureDomains())
                .thenReturn(List.of("bad.example.com", "jp.tsdns.top"));
        when(historyRepository.findLatest(anyString(), any())).thenReturn(Optional.empty());
        when(ecsDnsClient.resolveA(eq("bad.example.com"), anyString()))
                .thenThrow(new IllegalStateException("DNS 不通"));
        when(ecsDnsClient.resolveA(eq("jp.tsdns.top"), anyString())).thenReturn(List.of("13.192.233.178"));

        assertThatCode(() -> service.watchAll()).doesNotThrowAnyException();

        verify(historyRepository, times(1)).create(any());
    }

    @Test
    @DisplayName("按 entryIps 相同顺序反查 ASN 并拼接写入 asns 列")
    void fillsAsnsInSameOrderAsEntryIps() {
        when(historyRepository.findLatest(anyString(), any())).thenReturn(Optional.empty());
        // 归一化按字典序排序后应为 13.192.233.178, 34.84.255.241
        when(ecsDnsClient.resolveA(anyString(), anyString()))
                .thenReturn(List.of("34.84.255.241", "13.192.233.178"));
        when(ipAsnClient.lookupAsn("13.192.233.178")).thenReturn(Optional.of("AS16509"));
        when(ipAsnClient.lookupAsn("34.84.255.241")).thenReturn(Optional.of("AS396982"));

        service.watchAll();

        ArgumentCaptor<EntryIpHistory> captor = ArgumentCaptor.forClass(EntryIpHistory.class);
        verify(historyRepository).create(captor.capture());
        assertThat(captor.getValue().getAsns()).isEqualTo("AS16509,AS396982");
    }

    @Test
    @DisplayName("部分 IP 反查不到 ASN，只在对应位置留空，不影响其余位置")
    void leavesBlankForIpsWithoutAsn() {
        when(historyRepository.findLatest(anyString(), any())).thenReturn(Optional.empty());
        when(ecsDnsClient.resolveA(anyString(), anyString()))
                .thenReturn(List.of("13.192.233.178", "34.84.255.241"));
        when(ipAsnClient.lookupAsn("13.192.233.178")).thenReturn(Optional.of("AS16509"));
        when(ipAsnClient.lookupAsn("34.84.255.241")).thenReturn(Optional.empty());

        service.watchAll();

        ArgumentCaptor<EntryIpHistory> captor = ArgumentCaptor.forClass(EntryIpHistory.class);
        verify(historyRepository).create(captor.capture());
        assertThat(captor.getValue().getAsns()).isEqualTo("AS16509,");
    }

    @Test
    @DisplayName("全部 IP 都反查不到 ASN 时 asns 整列为 NULL")
    void asnsNullWhenAllLookupsFail() {
        when(historyRepository.findLatest(anyString(), any())).thenReturn(Optional.empty());
        when(ecsDnsClient.resolveA(anyString(), anyString())).thenReturn(List.of("13.192.233.178"));
        when(ipAsnClient.lookupAsn(anyString())).thenReturn(Optional.empty());

        service.watchAll();

        ArgumentCaptor<EntryIpHistory> captor = ArgumentCaptor.forClass(EntryIpHistory.class);
        verify(historyRepository).create(captor.capture());
        assertThat(captor.getValue().getAsns()).isNull();
    }

    @Test
    @DisplayName("ASN 反查抛异常不影响主流程：仍正常落库该 IP 位置留空")
    void asnLookupFailureDoesNotBreakMainFlow() {
        when(historyRepository.findLatest(anyString(), any())).thenReturn(Optional.empty());
        when(ecsDnsClient.resolveA(anyString(), anyString())).thenReturn(List.of("13.192.233.178"));
        when(ipAsnClient.lookupAsn(anyString())).thenThrow(new IllegalStateException("ASN 服务不通"));

        assertThatCode(() -> service.watchAll()).doesNotThrowAnyException();

        verify(historyRepository).create(any());
    }
}
