package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.repository.EntryIpHistoryRepository;
import ai.mintpop.lane.repository.LinkReportDailyRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.LinkReportRepository.DomainIspAggregate;
import ai.mintpop.lane.response.LinkHealthResponse;
import ai.mintpop.lane.response.LinkHealthResponse.DomainRow;
import ai.mintpop.lane.response.LinkHealthResponse.EntryIpChange;
import ai.mintpop.lane.response.LinkHealthResponse.IspCell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯 Mockito 单测，不碰数据库，专注业务逻辑本身：
 * 是否按 days 决定读哪张表、超出范围时是否收敛、两张底表的 isp 编码合并、
 * successRate 的 null 语义、入口 IP 变更时间线的边界处理。
 * 端到端的路由接线与鉴权由 {@link ai.mintpop.lane.controller.admin.AdminLinkHealthControllerTest} 覆盖。
 */
class AdminLinkHealthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private static final String DOMAIN = "jp.tsdns.top";

    private LinkReportRepository linkReportRepository;
    private LinkReportDailyRepository linkReportDailyRepository;
    private EntryIpHistoryRepository entryIpHistoryRepository;
    private LinkReportProperties properties;
    private AdminLinkHealthServiceImpl service;

    @BeforeEach
    void setUp() {
        linkReportRepository = mock(LinkReportRepository.class);
        linkReportDailyRepository = mock(LinkReportDailyRepository.class);
        entryIpHistoryRepository = mock(EntryIpHistoryRepository.class);
        properties = new LinkReportProperties(); // 默认 rawRetentionDays=7、dailyRetentionDays=90

        when(linkReportRepository.aggregateGlobalByDomainAndIsp(any(), any())).thenReturn(List.of());
        when(linkReportDailyRepository.aggregateGlobalByDomainAndIsp(any(), any())).thenReturn(List.of());
        when(entryIpHistoryRepository.findAllOrderByDomainVantageAndTime()).thenReturn(List.of());

        service = new AdminLinkHealthServiceImpl(linkReportRepository, linkReportDailyRepository,
                entryIpHistoryRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private EntryIpHistory history(String domain, DnsVantage vantage, String ips, Instant observedAt) {
        EntryIpHistory h = new EntryIpHistory();
        h.setFailureDomain(domain);
        h.setVantage(vantage);
        h.setEntryIps(ips);
        h.setObservedAt(observedAt);
        return h;
    }

    @Test
    @DisplayName("days 落在原始保留期内，只查 link_report，不查 link_report_daily")
    void daysWithinRawRetentionSkipsDailyTable() {
        service.getLinkHealth(5); // < rawRetentionDays(7)

        verify(linkReportRepository).aggregateGlobalByDomainAndIsp(NOW.minus(Duration.ofDays(5)), NOW);
        verify(linkReportDailyRepository, never()).aggregateGlobalByDomainAndIsp(any(), any());
    }

    @Test
    @DisplayName("days 正好等于原始保留期这个边界上，仍只查 link_report")
    void daysExactlyAtRawRetentionStillSkipsDailyTable() {
        // 边界值单独立一条：读哪张表的判据是「严格大于 rawRetentionDays」，
        // 与归档任务 findWindowsBefore / deleteWindowsBefore 的「严格早于」语义配套。
        // 把 > 写成 >= 的话，days=7 会多查一张此刻还没有任何数据的 daily 表；
        // 反过来若归档那边改成非严格，这里就会漏掉刚被归档走的那一天。
        // 前后两条测试只覆盖了 5 和 10，这个边界原本是空的
        service.getLinkHealth(7); // == rawRetentionDays(7)

        verify(linkReportRepository).aggregateGlobalByDomainAndIsp(NOW.minus(Duration.ofDays(7)), NOW);
        verify(linkReportDailyRepository, never()).aggregateGlobalByDomainAndIsp(any(), any());
    }

    @Test
    @DisplayName("days 超过原始保留期，两张表都查")
    void daysAboveRawRetentionQueriesBothTables() {
        service.getLinkHealth(10); // > rawRetentionDays(7)

        Instant from = NOW.minus(Duration.ofDays(10));
        verify(linkReportRepository).aggregateGlobalByDomainAndIsp(from, NOW);
        verify(linkReportDailyRepository).aggregateGlobalByDomainAndIsp(
                LocalDate.ofInstant(from, ZoneOffset.UTC), LocalDate.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("days 超出按天聚合保留天数上限时收敛到上限，不会真的按原始值拖库查询")
    void requestedDaysAboveUpperBoundIsClamped() {
        service.getLinkHealth(999_999);

        Instant clampedFrom = NOW.minus(Duration.ofDays(properties.getDailyRetentionDays()));
        verify(linkReportRepository).aggregateGlobalByDomainAndIsp(clampedFrom, NOW);
    }

    @Test
    @DisplayName("days 传 0 或负数收敛到下限 1 天，不是查出一个空区间或报错")
    void requestedDaysAtOrBelowZeroIsClampedToOne() {
        service.getLinkHealth(0);

        verify(linkReportRepository).aggregateGlobalByDomainAndIsp(NOW.minus(Duration.ofDays(1)), NOW);
    }

    @Test
    @DisplayName("原始表 isp=null 与按天聚合表 isp=空串（已归一）合并成同一个格子")
    void mergesUnresolvedIspFromBothTablesIntoOneCell() {
        when(linkReportRepository.aggregateGlobalByDomainAndIsp(any(), any()))
                .thenReturn(List.of(new DomainIspAggregate(DOMAIN, null, 10, 9, 1)));
        // LinkReportDailyRepository 契约已在 repository 层把空串归一成 null，这里直接按该约定构造
        when(linkReportDailyRepository.aggregateGlobalByDomainAndIsp(any(), any()))
                .thenReturn(List.of(new DomainIspAggregate(DOMAIN, null, 5, 4, 0)));

        LinkHealthResponse response = service.getLinkHealth(30); // 触发两张表都查

        assertThat(response.domains()).hasSize(1);
        DomainRow row = response.domains().get(0);
        assertThat(row.isps()).hasSize(1);
        IspCell cell = row.isps().get(0);
        assertThat(cell.isp()).isEqualTo("");
        assertThat(cell.samples()).isEqualTo(15);
        assertThat(cell.aliveCount()).isEqualTo(13);
        assertThat(row.samples()).isEqualTo(15);
        assertThat(row.aliveCount()).isEqualTo(13);
        assertThat(row.failovers()).isEqualTo(1);
    }

    @Test
    @DisplayName("samples 为 0 时 successRate 必须是 null，不是 0.0")
    void successRateIsNullNotZeroWhenNoSamples() {
        when(linkReportRepository.aggregateGlobalByDomainAndIsp(any(), any()))
                .thenReturn(List.of(new DomainIspAggregate(DOMAIN, "CTC", 0, 0, 0)));

        LinkHealthResponse response = service.getLinkHealth(5);

        IspCell cell = response.domains().get(0).isps().get(0);
        assertThat(cell.successRate()).isNull();
    }

    @Test
    @DisplayName("有样本时 successRate 正常算出比值")
    void successRateComputedWhenSamplesPositive() {
        when(linkReportRepository.aggregateGlobalByDomainAndIsp(any(), any()))
                .thenReturn(List.of(new DomainIspAggregate(DOMAIN, "CTC", 10, 7, 0)));

        LinkHealthResponse response = service.getLinkHealth(5);

        IspCell cell = response.domains().get(0).isps().get(0);
        assertThat(cell.successRate()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("故障域行的 samples/aliveCount/failovers 是该域下全部运营商之和")
    void domainRowSumsAcrossIsps() {
        when(linkReportRepository.aggregateGlobalByDomainAndIsp(any(), any())).thenReturn(List.of(
                new DomainIspAggregate(DOMAIN, "CTC", 10, 9, 1),
                new DomainIspAggregate(DOMAIN, "CUCC", 20, 15, 2)));

        LinkHealthResponse response = service.getLinkHealth(5);

        DomainRow row = response.domains().get(0);
        assertThat(row.isps()).hasSize(2);
        assertThat(row.samples()).isEqualTo(30);
        assertThat(row.aliveCount()).isEqualTo(24);
        assertThat(row.failovers()).isEqualTo(3);
    }

    @Test
    @DisplayName("入口 IP 变更时间线：组内第一条只是基线不算变更，第二条起才是变更，且按最近优先排序")
    void entryIpTimelineReportsChangesAfterBaselineMostRecentFirst() {
        when(entryIpHistoryRepository.findAllOrderByDomainVantageAndTime()).thenReturn(List.of(
                history(DOMAIN, DnsVantage.OVERSEAS, "1.1.1.1", NOW.minus(Duration.ofDays(6))), // 基线，落在窗口内
                history(DOMAIN, DnsVantage.OVERSEAS, "2.2.2.2", NOW.minus(Duration.ofDays(3))),
                history(DOMAIN, DnsVantage.OVERSEAS, "3.3.3.3", NOW.minus(Duration.ofDays(1)))));

        LinkHealthResponse response = service.getLinkHealth(7);

        assertThat(response.entryIpTimeline()).hasSize(2);
        EntryIpChange latest = response.entryIpTimeline().get(0);
        assertThat(latest.previousIps()).isEqualTo("2.2.2.2");
        assertThat(latest.currentIps()).isEqualTo("3.3.3.3");
        assertThat(latest.changedAt()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        assertThat(latest.vantage()).isEqualTo("OVERSEAS");
        assertThat(latest.failureDomain()).isEqualTo(DOMAIN);

        EntryIpChange earlier = response.entryIpTimeline().get(1);
        assertThat(earlier.previousIps()).isEqualTo("1.1.1.1");
        assertThat(earlier.currentIps()).isEqualTo("2.2.2.2");
    }

    @Test
    @DisplayName("查询窗口之前的基线在窗口之前找不到对手，也能正确识别进入窗口后的第一次变更")
    void entryIpTimelineComparesAgainstBaselineBeforeWindow() {
        when(entryIpHistoryRepository.findAllOrderByDomainVantageAndTime()).thenReturn(List.of(
                history(DOMAIN, DnsVantage.OVERSEAS, "1.1.1.1", NOW.minus(Duration.ofDays(20))), // 窗口之前的基线
                history(DOMAIN, DnsVantage.OVERSEAS, "2.2.2.2", NOW.minus(Duration.ofDays(2)))));  // 窗口内的变更

        LinkHealthResponse response = service.getLinkHealth(7); // from = NOW - 7 天

        assertThat(response.entryIpTimeline()).hasSize(1);
        EntryIpChange change = response.entryIpTimeline().get(0);
        assertThat(change.previousIps()).isEqualTo("1.1.1.1");
        assertThat(change.currentIps()).isEqualTo("2.2.2.2");
    }

    @Test
    @DisplayName("变更发生在窗口之前时不出现在时间线里")
    void entryIpTimelineExcludesChangesBeforeWindow() {
        when(entryIpHistoryRepository.findAllOrderByDomainVantageAndTime()).thenReturn(List.of(
                history(DOMAIN, DnsVantage.OVERSEAS, "1.1.1.1", NOW.minus(Duration.ofDays(20))),
                history(DOMAIN, DnsVantage.OVERSEAS, "2.2.2.2", NOW.minus(Duration.ofDays(15)))));

        LinkHealthResponse response = service.getLinkHealth(7);

        assertThat(response.entryIpTimeline()).isEmpty();
    }

    @Test
    @DisplayName("同一故障域不同视角各自独立比较，不会跨视角误判成变更")
    void entryIpTimelineComparesWithinSameVantageOnly() {
        when(entryIpHistoryRepository.findAllOrderByDomainVantageAndTime()).thenReturn(List.of(
                history(DOMAIN, DnsVantage.CHINA_TELECOM, "9.9.9.9", NOW.minus(Duration.ofDays(6))),
                history(DOMAIN, DnsVantage.OVERSEAS, "1.1.1.1", NOW.minus(Duration.ofDays(6))),
                history(DOMAIN, DnsVantage.OVERSEAS, "2.2.2.2", NOW.minus(Duration.ofDays(1)))));

        LinkHealthResponse response = service.getLinkHealth(7);

        // CHINA_TELECOM 只有一条（基线），不产生变更；OVERSEAS 两条产生一次变更
        assertThat(response.entryIpTimeline()).hasSize(1);
        assertThat(response.entryIpTimeline().get(0).vantage()).isEqualTo("OVERSEAS");
    }
}
