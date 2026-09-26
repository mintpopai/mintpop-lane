package ai.mintpop.lane.service;

import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.IpAsnClient.AsnInfo;
import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.repository.AsnOrgRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.request.LinkHeartbeatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link LinkReportServiceImpl} 单测：JSON 解析、契约字段映射、窗口时间范围校验、
 * ASN 反查、异常兜底。
 * 心跳承载的是「用户还能不能用」，本类反复验证的核心不变量是——
 * ingest 无论输入多脏（语法错误的 JSON、类型不匹配的字段、越界的窗口时间、
 * 下游异常），都绝不向外抛异常，格式/范围有问题时最多整条上报块被丢弃。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("链路上报落库")
class LinkReportServiceTest {

    @Mock
    private LinkReportRepository linkReportRepository;

    @Mock
    private IpAsnClient ipAsnClient;

    @Mock
    private AsnOrgRepository asnOrgRepository;

    @Mock
    private Clock clock;

    private LinkReportService service;
    private final ObjectMapper objectMapper = new JsonMapper();
    private final LinkReportProperties properties = new LinkReportProperties();

    private static final Long USER_ID = 1L;
    private static final String SOURCE_IP = "203.0.113.9";

    /** 测试用的固定「现在」；下面各上报窗口的 windowStart 都以它为基准取相对偏移 */
    private static final Instant NOW = Instant.parse("2026-09-19T00:03:00Z");

    @BeforeEach
    void setUp() {
        service = new LinkReportServiceImpl(linkReportRepository, ipAsnClient, asnOrgRepository, objectMapper,
                clock, properties);
    }

    private LinkHeartbeatRequest newRequest(String failureDomain, Instant windowStart) {
        return newRequest(failureDomain, windowStart, 10, 9, 0);
    }

    private LinkHeartbeatRequest newRequest(String failureDomain, Instant windowStart, Integer samples,
                                            Integer alive, Integer noSample) {
        LinkHeartbeatRequest request = new LinkHeartbeatRequest();
        request.setFailureDomain(failureDomain);
        request.setWindowStart(windowStart);
        LinkHeartbeatRequest.Window window = new LinkHeartbeatRequest.Window();
        window.setSamples(samples);
        window.setAlive(alive);
        window.setNoSample(noSample);
        request.setWindow(window);
        request.setFailovers(0);
        return request;
    }

    private String json(LinkHeartbeatRequest request) {
        return objectMapper.writeValueAsString(request);
    }

    // —— 契约字段映射 ——

    @Test
    @DisplayName("failureDomain 为 null 时落库存空串")
    void nullFailureDomainStoresAsEmptyString() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, json(newRequest(null, NOW.minusSeconds(180))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("");
    }

    @Test
    @DisplayName("具体故障域原样落库，不做任何转换")
    void concreteFailureDomainPassesThroughUnchanged() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("jp.tsdns.top");
    }

    // —— ASN 反查 ——

    @Test
    @DisplayName("反查成功时 ASN 落 source_asn——运营商维度以它做键，展示名不进这张窗口表")
    void lookupSuccessStoresSourceAsn() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.of(new AsnInfo("AS4134", "China Telecom")));

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        LinkReport persisted = captor.getValue();
        assertThat(persisted.getSourceAsn()).isEqualTo("AS4134");
    }

    @Test
    @DisplayName("上游给的运营商名超长时截到 64 字符再写 asn_org，本窗口照常落库——名字不进 link_report")
    void overlongOrgNameDoesNotAffectWindowPersisting() {
        when(clock.instant()).thenReturn(NOW);
        String longOrgName = "China Networks Inter-Exchange, China Telecommunications Corporation";
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.of(new AsnInfo("AS4134", longOrgName)));

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        LinkReport persisted = captor.getValue();
        assertThat(persisted.getSourceAsn()).isEqualTo("AS4134");

        // 超长的风险随展示名一起挪到了 asn_org.org_name（VARCHAR(64)）：写进去之前就截断，
        // 落库的值由我们决定，而不是交给 MySQL 静默砍（asn_org 是「有则不动」，砍错了改不掉）
        ArgumentCaptor<String> orgNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(asnOrgRepository).insertIfAbsent(eq("AS4134"), orgNameCaptor.capture(), eq(NOW));
        assertThat(longOrgName.length()).isGreaterThan(IpAsnClient.ISP_MAX_LENGTH); // 这条样本确实超长
        assertThat(orgNameCaptor.getValue())
                .hasSize(IpAsnClient.ISP_MAX_LENGTH)
                .isEqualTo(longOrgName.substring(0, IpAsnClient.ISP_MAX_LENGTH));
    }

    @Test
    @DisplayName("反查成功时 source_asn 落库，展示名首次写入 asn_org；同一 ASN 换了文案不覆盖")
    void ingestRecordsAsnAndKeepsFirstSeenOrgName() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP))
                .thenReturn(Optional.of(new AsnInfo("AS4134", "China Telecom")))
                .thenReturn(Optional.of(new AsnInfo("AS4134", "CHINANET-BACKBONE")));

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);
        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(120))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository, times(2)).upsertWindow(captor.capture());
        assertThat(captor.getAllValues()).extracting(LinkReport::getSourceAsn)
                .containsExactly("AS4134", "AS4134");

        // 服务层每次都走「有则不动」的 insertIfAbsent，自己不判断要不要覆盖：
        // 「第二次的文案盖不掉第一次」是 asn_org 的 INSERT IGNORE 语义，由
        // AsnOrgRepositoryTest#secondInsertKeepsFirstSeenName 与走真库真入口的
        // LinkControllerTest#heartbeatKeepsFirstSeenOrgNameForSameAsn 守住
        verify(asnOrgRepository).insertIfAbsent("AS4134", "China Telecom", NOW);
        verify(asnOrgRepository).insertIfAbsent("AS4134", "CHINANET-BACKBONE", NOW);
        verify(asnOrgRepository, times(2)).insertIfAbsent(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("反查失败时 source_asn 为 NULL，且不往 asn_org 写任何东西")
    void lookupFailureLeavesAsnNullAndAsnOrgUntouched() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        assertThat(captor.getValue().getSourceAsn()).isNull();
        // 反查整体失败时连 ASN 都没有，也就没有键可以挂展示名——这张映射表一个字都不该动
        verifyNoInteractions(asnOrgRepository);
    }

    @Test
    @DisplayName("记展示名失败不影响窗口落库——asn_org 是旁路，link_report 才是这一期的全部价值")
    void asnOrgWriteFailureDoesNotDropTheWindow() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.of(new AsnInfo("AS4134", "China Telecom")));
        // 迁移没跑到、表权限不对、死锁……这类故障是持续性的：若两次写入共用一个 try、
        // 而且展示名还排在窗口之前，所有反查成功的上报都会长期静默停摆（心跳照样 200）
        doThrow(new RuntimeException("asn_org 写入失败"))
                .when(asnOrgRepository).insertIfAbsent(anyString(), anyString(), any());

        assertThatCode(() -> service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP))
                .doesNotThrowAnyException();

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        // 窗口照落，ASN 也照填——丢掉的只是「这个 ASN 叫什么」，文案退回 AS 号即可
        assertThat(captor.getValue().getSourceAsn()).isEqualTo("AS4134");
    }

    @Test
    @DisplayName("反查到 ASN 但没有展示名时只落 ASN，asn_org 不写空名字")
    void lookupWithoutOrgNameSkipsAsnOrg() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.of(new AsnInfo("AS4134", null)));

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);

        ArgumentCaptor<LinkReport> captor = ArgumentCaptor.forClass(LinkReport.class);
        verify(linkReportRepository).upsertWindow(captor.capture());
        // 名字缺失不挡 ASN 落库：运营商维度以 ASN 做键，没有展示名只是文案上退回 ASN，
        // 这段样本照样进得了运营商级判定
        assertThat(captor.getValue().getSourceAsn()).isEqualTo("AS4134");
        // org_name 是 NOT NULL：写 null 在严格模式下会被拒收，退而写空串则等于给这个 ASN
        // 钉死一个空展示名（INSERT IGNORE 之后再也改不掉），不如干脆不写、让文案退回 ASN
        verifyNoInteractions(asnOrgRepository);
    }

    // —— 异常兜底：下游异常、格式残缺 ——

    @Test
    @DisplayName("落库异常被吞掉，不向外抛出——心跳不能被上报拖挂")
    void persistenceFailureIsSwallowed() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down")).when(linkReportRepository).upsertWindow(any());

        assertThatCode(() -> service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("上报块残缺（window 为 null）不向外抛出，且不落库")
    void malformedReportMissingWindowIsSwallowedWithoutPersisting() {
        when(clock.instant()).thenReturn(NOW);

        LinkHeartbeatRequest request = newRequest("jp.tsdns.top", NOW.minusSeconds(180));
        request.setWindow(null);

        assertThatCode(() -> service.ingest(USER_ID, json(request), SOURCE_IP))
                .doesNotThrowAnyException();

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    // —— C1 修复：JSON 层面的错误必须被 ingest 自己的 try/catch 吞掉，不能让 Spring/Jackson
    //    在方法体执行前就把请求判成参数错误（那会让整条心跳失败，客户端因此误判链路失效断链）——
    //    这类输入现在应该在 objectMapper.readValue 这一步就抛出，根本不会摸到 clock/ipAsnClient，
    //    所以这几条不 stub 它们，也不应该被调用

    @Test
    @DisplayName("JSON 语法错误不向外抛出，且不落库")
    void syntacticallyInvalidJsonIsSwallowedWithoutPersisting() {
        assertThatCode(() -> service.ingest(USER_ID, "{{{", SOURCE_IP))
                .doesNotThrowAnyException();

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("字段类型完全不匹配（window 是字符串不是对象）不向外抛出，且不落库")
    void fieldTypeMismatchIsSwallowedWithoutPersisting() {
        assertThatCode(() -> service.ingest(USER_ID, "{\"window\":\"not-an-object\"}", SOURCE_IP))
                .doesNotThrowAnyException();

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("数字字段传了非数字字符串（JSON 语法合法但类型全错）不向外抛出，且不落库")
    void numericFieldWithNonNumericStringIsSwallowedWithoutPersisting() {
        assertThatCode(() -> service.ingest(USER_ID, "{\"failovers\":\"abc\"}", SOURCE_IP))
                .doesNotThrowAnyException();

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    // —— I1 修复：窗口起点必须落在 [现在 - windowMaxPast, 现在 + windowMaxFuture] 内 ——

    @Test
    @DisplayName("窗口起点在未来超过容忍范围（默认 5 分钟）时丢弃，不落库")
    void futureWindowOutsideToleranceIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        Instant tooFarInFuture = NOW.plusSeconds(600); // 10 分钟后，超过默认 5 分钟容忍
        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", tooFarInFuture)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("窗口起点在过去超过容忍范围（默认 1 小时）时丢弃，不落库")
    void pastWindowOutsideToleranceIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        Instant tooFarInPast = NOW.minusSeconds(7200); // 2 小时前，超过默认 1 小时容忍
        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", tooFarInPast)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("窗口起点在容忍范围内时正常落库")
    void windowWithinToleranceIsPersisted() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());

        Instant withinTolerance = NOW.minusSeconds(1800); // 30 分钟前，在默认 1 小时容忍内
        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", withinTolerance)), SOURCE_IP);

        verify(linkReportRepository).upsertWindow(any());
    }

    @Test
    @DisplayName("窗口起点缺失（null）视同越界，丢弃，不落库")
    void missingWindowStartIsDiscarded() {
        // windowStart 为 null 时短路返回，压根不会调 clock.instant()，这里不 stub 它

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", null)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    // —— I2 修复：计数合理性校验。客户端 bug 或手工构造的请求可以写进 alive > samples、
    //    负样本、负延迟：spec §8.1 因为「自报不可信」才让运营商由服务端反查，计数同理。
    //    负 samples 会让告警的 samples <= 0 门槛把整段判定跳过，alive > samples 会算出
    //    >100% 的成功率把真实劣化掩盖掉，两者都会进全库矩阵

    @Test
    @DisplayName("alive 大于 samples 时整块丢弃：成功率不可能超过 100%，这种行会把真实劣化掩盖掉")
    void aliveGreaterThanSamplesIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180), 10, 11, 0)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("samples 为负数时整块丢弃：负样本会让告警的样本量门槛把整段判定跳过")
    void negativeSamplesIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180), -5, 0, 0)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("alive 为负数时整块丢弃")
    void negativeAliveIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180), 10, -1, 0)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("noSample 为负数时整块丢弃")
    void negativeNoSampleIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180), 10, 9, -3)), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("failovers 为负数时整块丢弃")
    void negativeFailoversIsDiscarded() {
        when(clock.instant()).thenReturn(NOW);

        LinkHeartbeatRequest request = newRequest("jp.tsdns.top", NOW.minusSeconds(180));
        request.setFailovers(-1);
        service.ingest(USER_ID, json(request), SOURCE_IP);

        verify(linkReportRepository, never()).upsertWindow(any());
    }

    @Test
    @DisplayName("p50 延迟为负数时整块丢弃；p50 为 null 是合法的（窗口内没有 alive 样本）")
    void negativeP50LatencyIsDiscardedButNullIsFine() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());

        LinkHeartbeatRequest negative = newRequest("jp.tsdns.top", NOW.minusSeconds(180));
        negative.setP50LatencyMs(-1);
        service.ingest(USER_ID, json(negative), SOURCE_IP);
        verify(linkReportRepository, never()).upsertWindow(any());

        // p50 为 null 走的是另一条路：契约里它本来就可空，不能被当成不合理计数一起丢掉
        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180))), SOURCE_IP);
        verify(linkReportRepository).upsertWindow(any());
    }

    @Test
    @DisplayName("alive 恰好等于 samples（全通）与全 0 的窗口都是合法的，不能被校验误杀")
    void fullySuccessfulAndAllZeroWindowsArePersisted() {
        when(clock.instant()).thenReturn(NOW);
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.empty());

        service.ingest(USER_ID, json(newRequest("jp.tsdns.top", NOW.minusSeconds(180), 10, 10, 0)), SOURCE_IP);
        // 整段窗口离线：一个样本也没有，是真实会发生的形态，不是脏数据
        service.ingest(USER_ID, json(newRequest("us.tsdns.top", NOW.minusSeconds(180), 0, 0, 5)), SOURCE_IP);

        verify(linkReportRepository, times(2)).upsertWindow(any());
    }
}
