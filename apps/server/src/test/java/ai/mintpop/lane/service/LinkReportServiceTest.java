package ai.mintpop.lane.service;

import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.request.LinkHeartbeatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LinkReportServiceImpl} 单测：契约字段映射、ASN 反查、异常兜底。
 * 心跳承载的是「用户还能不能用」，本类反复验证的核心不变量是——
 * ingest 无论输入多脏、下游多失败，都绝不向外抛异常。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("链路上报落库")
class LinkReportServiceTest {

    @Mock
    private LinkReportRepository linkReportRepository;

    @Mock
    private IpAsnClient ipAsnClient;

    private LinkReportService service;

    private static final Long USER_ID = 1L;
    private static final String SOURCE_IP = "203.0.113.9";

    @BeforeEach
    void setUp() {
        service = new LinkReportServiceImpl(linkReportRepository, ipAsnClient);
    }

    private LinkHeartbeatRequest newRequest(String failureDomain) {
        LinkHeartbeatRequest request = new LinkHeartbeatRequest();
        request.setFailureDomain(failureDomain);
        request.setWindowStart(Instant.parse("2026-09-19T00:00:00Z"));
        LinkHeartbeatRequest.Window window = new LinkHeartbeatRequest.Window();
        window.setSamples(10);
        window.setAlive(9);
        window.setNoSample(0);
        request.setWindow(window);
        request.setFailovers(0);
        return request;
    }

    @Test
    @DisplayName("failureDomain 为 null 时落库存空串")
    void nullFailureDomainStoresAsEmptyString() {
        when(ipAsnClient.lookupAsn(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, newRequest(null), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("");
    }

    @Test
    @DisplayName("具体故障域原样落库，不做任何转换")
    void concreteFailureDomainPassesThroughUnchanged() {
        when(ipAsnClient.lookupAsn(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, newRequest("jp.tsdns.top"), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("jp.tsdns.top");
    }

    @Test
    @DisplayName("ASN 反查失败不影响上报落库，只是 asn 与 isp 为 null")
    void asnLookupFailureStillPersistsReport() {
        when(ipAsnClient.lookupAsn(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, newRequest("jp.tsdns.top"), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        LinkReport persisted = captor.getValue();
        assertThat(persisted.getSourceAsn()).isNull();
        assertThat(persisted.getIsp()).isNull();
    }

    @Test
    @DisplayName("ASN 反查成功时落库 sourceAsn，isp 仍为 null——IpAsnClient 只返回 ASN，不提供运营商名")
    void asnLookupSuccessStoresAsnButNotIsp() {
        when(ipAsnClient.lookupAsn(SOURCE_IP)).thenReturn(Optional.of("AS4134"));

        service.ingest(USER_ID, newRequest("jp.tsdns.top"), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        LinkReport persisted = captor.getValue();
        assertThat(persisted.getSourceAsn()).isEqualTo("AS4134");
        assertThat(persisted.getIsp()).isNull();
    }

    @Test
    @DisplayName("落库异常被吞掉，不向外抛出——心跳不能被上报拖挂")
    void persistenceFailureIsSwallowed() {
        when(ipAsnClient.lookupAsn(SOURCE_IP)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down")).when(linkReportRepository).upsertWindow(any());

        assertThatCode(() -> service.ingest(USER_ID, newRequest("jp.tsdns.top"), SOURCE_IP))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("上报块残缺（window 为 null）不向外抛出，且不落库")
    void malformedReportMissingWindowIsSwallowedWithoutPersisting() {
        LinkHeartbeatRequest request = newRequest("jp.tsdns.top");
        request.setWindow(null);

        assertThatCode(() -> service.ingest(USER_ID, request, SOURCE_IP))
                .doesNotThrowAnyException();

        verify(linkReportRepository, never()).upsertWindow(any());
    }
}
