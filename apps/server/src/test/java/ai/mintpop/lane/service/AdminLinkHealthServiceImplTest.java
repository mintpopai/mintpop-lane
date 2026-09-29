package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.repository.AsnOrgRepository;
import ai.mintpop.lane.repository.LinkReportDailyRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.LinkReportRepository.DomainAsnAggregate;
import ai.mintpop.lane.response.LinkHealthResponse;
import ai.mintpop.lane.response.LinkHealthResponse.DomainRow;
import ai.mintpop.lane.response.LinkHealthResponse.AsnCell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯 Mockito 单测，不碰数据库，专注业务逻辑本身：
 * 是否按 days 决定读哪张表、超出范围时是否收敛、两张底表的 asn 编码合并、
 * successRate 的 null 语义、入口 IP 变更时间线的边界处理。
 * 端到端的路由接线与鉴权由 {@link ai.mintpop.lane.controller.admin.AdminLinkHealthControllerTest} 覆盖。
 */
class AdminLinkHealthServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private static final String DOMAIN = "jp.tsdns.top";

    private LinkReportRepository linkReportRepository;
    private LinkReportDailyRepository linkReportDailyRepository;
    private AsnOrgRepository asnOrgRepository;
    private LinkReportProperties properties;
    private AdminLinkHealthServiceImpl service;

    @BeforeEach
    void setUp() {
        linkReportRepository = mock(LinkReportRepository.class);
        linkReportDailyRepository = mock(LinkReportDailyRepository.class);
        asnOrgRepository = mock(AsnOrgRepository.class);
        properties = new LinkReportProperties(); // 默认 rawRetentionDays=7、dailyRetentionDays=90

        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any())).thenReturn(List.of());
        when(linkReportDailyRepository.aggregateGlobalByDomainAndAsn(any(), any())).thenReturn(List.of());
        // 默认「一个展示名都没记过」：展示名是可选的旁路数据，绝大多数用例不关心它
        when(asnOrgRepository.findAllNames()).thenReturn(Map.of());

        service = new AdminLinkHealthServiceImpl(linkReportRepository, linkReportDailyRepository,
                asnOrgRepository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("days 落在原始保留期内，只查 link_report，不查 link_report_daily")
    void daysWithinRawRetentionSkipsDailyTable() {
        service.getLinkHealth(5); // < rawRetentionDays(7)

        verify(linkReportRepository).aggregateGlobalByDomainAndAsn(NOW.minus(Duration.ofDays(5)), NOW);
        verify(linkReportDailyRepository, never()).aggregateGlobalByDomainAndAsn(any(), any());
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

        verify(linkReportRepository).aggregateGlobalByDomainAndAsn(NOW.minus(Duration.ofDays(7)), NOW);
        verify(linkReportDailyRepository, never()).aggregateGlobalByDomainAndAsn(any(), any());
    }

    @Test
    @DisplayName("days 超过原始保留期，两张表都查")
    void daysAboveRawRetentionQueriesBothTables() {
        service.getLinkHealth(10); // > rawRetentionDays(7)

        Instant from = NOW.minus(Duration.ofDays(10));
        verify(linkReportRepository).aggregateGlobalByDomainAndAsn(from, NOW);
        verify(linkReportDailyRepository).aggregateGlobalByDomainAndAsn(
                LocalDate.ofInstant(from, ZoneOffset.UTC), LocalDate.ofInstant(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("days 超出按天聚合保留天数上限时收敛到上限，不会真的按原始值拖库查询")
    void requestedDaysAboveUpperBoundIsClamped() {
        service.getLinkHealth(999_999);

        Instant clampedFrom = NOW.minus(Duration.ofDays(properties.getDailyRetentionDays()));
        verify(linkReportRepository).aggregateGlobalByDomainAndAsn(clampedFrom, NOW);
    }

    @Test
    @DisplayName("days 传 0 或负数收敛到下限 1 天，不是查出一个空区间或报错")
    void requestedDaysAtOrBelowZeroIsClampedToOne() {
        service.getLinkHealth(0);

        verify(linkReportRepository).aggregateGlobalByDomainAndAsn(NOW.minus(Duration.ofDays(1)), NOW);
    }

    @Test
    @DisplayName("原始表 asn=null 与按天聚合表 asn=空串（已归一）合并成同一个格子")
    void mergesUnresolvedAsnFromBothTablesIntoOneCell() {
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any()))
                .thenReturn(List.of(new DomainAsnAggregate(DOMAIN, null, 10, 9, 1)));
        // LinkReportDailyRepository 契约已在 repository 层把空串归一成 null，这里直接按该约定构造
        when(linkReportDailyRepository.aggregateGlobalByDomainAndAsn(any(), any()))
                .thenReturn(List.of(new DomainAsnAggregate(DOMAIN, null, 5, 4, 0)));

        LinkHealthResponse response = service.getLinkHealth(30); // 触发两张表都查

        assertThat(response.domains()).hasSize(1);
        DomainRow row = response.domains().get(0);
        assertThat(row.asns()).hasSize(1);
        AsnCell cell = row.asns().get(0);
        assertThat(cell.asn()).isEqualTo("");
        assertThat(cell.samples()).isEqualTo(15);
        assertThat(cell.aliveCount()).isEqualTo(13);
        assertThat(row.samples()).isEqualTo(15);
        assertThat(row.aliveCount()).isEqualTo(13);
        assertThat(row.failovers()).isEqualTo(1);
    }

    @Test
    @DisplayName("samples 为 0 时 successRate 必须是 null，不是 0.0")
    void successRateIsNullNotZeroWhenNoSamples() {
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any()))
                .thenReturn(List.of(new DomainAsnAggregate(DOMAIN, "AS4134", 0, 0, 0)));

        LinkHealthResponse response = service.getLinkHealth(5);

        AsnCell cell = response.domains().get(0).asns().get(0);
        assertThat(cell.successRate()).isNull();
    }

    @Test
    @DisplayName("有样本时 successRate 正常算出比值")
    void successRateComputedWhenSamplesPositive() {
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any()))
                .thenReturn(List.of(new DomainAsnAggregate(DOMAIN, "AS4134", 10, 7, 0)));

        LinkHealthResponse response = service.getLinkHealth(5);

        AsnCell cell = response.domains().get(0).asns().get(0);
        assertThat(cell.successRate()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("故障域行的 samples/aliveCount/failovers 是该域下全部运营商之和")
    void domainRowSumsAcrossAsns() {
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any())).thenReturn(List.of(
                new DomainAsnAggregate(DOMAIN, "AS4134", 10, 9, 1),
                new DomainAsnAggregate(DOMAIN, "AS4837", 20, 15, 2)));

        LinkHealthResponse response = service.getLinkHealth(5);

        DomainRow row = response.domains().get(0);
        assertThat(row.asns()).hasSize(2);
        assertThat(row.samples()).isEqualTo(30);
        assertThat(row.aliveCount()).isEqualTo(24);
        assertThat(row.failovers()).isEqualTo(3);
    }

    @Test
    @DisplayName("矩阵按 ASN 分列并带上展示名；asn_org 没记过的 ASN 也照常出列、orgName 为 null")
    void cellsAreKeyedByAsnWithOptionalOrgName() {
        // 展示名是「锦上添花」而不是出列的前提：asn_org 里没记过名字的 ASN（上游当时没给名字、
        // 或那次旁路写入失败）若因此被漏掉，矩阵会凭空少掉整整一列真实流量，比显示一串 AS 号糟得多。
        // 反查失败的那组（asn=null → 空串）同样是一列，且它永远取不到展示名
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any())).thenReturn(List.of(
                new DomainAsnAggregate(DOMAIN, "AS4134", 100, 90, 0),
                new DomainAsnAggregate(DOMAIN, "AS9808", 50, 40, 0),
                new DomainAsnAggregate(DOMAIN, null, 10, 10, 0)));
        when(asnOrgRepository.findAllNames()).thenReturn(Map.of("AS4134", "China Telecom"));

        List<AsnCell> cells = service.getLinkHealth(7).domains().get(0).asns();

        // 顺序按样本量倒序（100 / 50 / 10），见 cellsAreSortedBySamplesDescThenAsnAsc
        assertThat(cells).extracting(AsnCell::asn, AsnCell::orgName)
                .containsExactly(tuple("AS4134", "China Telecom"), tuple("AS9808", null), tuple("", null));
    }

    @Test
    @DisplayName("格子按样本量倒序排，样本多的运营商排在前面；样本相同时按 ASN 串升序，空串 asn（未知运营商）"
            + "因此排在同数那几格的最前")
    void cellsAreSortedBySamplesDescThenAsnAsc() {
        // 按 ASN 串字典序排会把「AS1 打头的小运营商」顶到第一列，而这张矩阵是用来看「哪家出问题」的：
        // 目光该先落在流量最大的那几家上——占了大头的运营商成功率掉下去才是事故，
        // 一天只有几个样本的运营商成功率 0% 多半只是噪声。故主序是样本量倒序。
        // 样本相同再按 ASN 串升序，只是为了让同一次请求两次调用给出稳定顺序，不是业务要求。
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any())).thenReturn(List.of(
                new DomainAsnAggregate(DOMAIN, "AS9808", 10, 9, 0),
                new DomainAsnAggregate(DOMAIN, "AS4837", 100, 90, 0),
                new DomainAsnAggregate(DOMAIN, "AS4134", 10, 10, 0),
                new DomainAsnAggregate(DOMAIN, null, 10, 10, 0)));

        List<AsnCell> cells = service.getLinkHealth(7).domains().get(0).asns();

        assertThat(cells).extracting(AsnCell::asn, AsnCell::samples)
                .containsExactly(tuple("AS4837", 100L),
                        tuple("", 10L), tuple("AS4134", 10L), tuple("AS9808", 10L));
    }

    @Test
    @DisplayName("asn_org 查询失败时矩阵照常返回、只是没有展示名，不把整个链路健康页打成 500")
    void orgNameLookupFailureDegradesToAsnOnlyInsteadOfFailingWholeQuery() {
        // asn_org 是装饰用的旁路表（迁移没跑到、表权限不对都可能让它查不出来），而样本与成功率
        // 才是这个页面的全部价值。让这张表的故障把页面打成 500，等于在最需要看链路状况的时候
        // 什么都看不到。取舍与 LinkReportAlertService.resolveOrgNames 完全一致
        when(linkReportRepository.aggregateGlobalByDomainAndAsn(any(), any()))
                .thenReturn(List.of(new DomainAsnAggregate(DOMAIN, "AS4134", 100, 90, 0)));
        when(asnOrgRepository.findAllNames()).thenThrow(new RuntimeException("asn_org 表不存在"));

        List<AsnCell> cells = service.getLinkHealth(7).domains().get(0).asns();

        assertThat(cells).extracting(AsnCell::asn, AsnCell::orgName, AsnCell::samples)
                .containsExactly(tuple("AS4134", null, 100L));
    }

}
