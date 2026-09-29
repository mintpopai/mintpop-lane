package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.repository.AsnOrgRepository;
import ai.mintpop.lane.repository.LinkReportDailyRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.LinkReportRepository.DomainAsnAggregate;
import ai.mintpop.lane.response.LinkHealthResponse;
import ai.mintpop.lane.response.LinkHealthResponse.AsnCell;
import ai.mintpop.lane.response.LinkHealthResponse.DomainRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 管理端链路健康查询的实现。查询同时覆盖 {@code link_report}（保留期内的原始窗口）与
 * {@code link_report_daily}（更早的按天聚合），按 {@code days} 决定读哪张或两张都读——
 * {@code days} 落在原始保留期内时，请求的数据必然全部还在原始表（归档任务只搬运早于
 * {@link LinkReportProperties#getRawRetentionDays()} 的窗口，落在保留期内的窗口不可能被
 * 归档掉），没必要多查一次按天聚合表。
 */
@Slf4j
@Service
public class AdminLinkHealthServiceImpl implements AdminLinkHealthService {

    /** days 参数下限：至少查 1 天，0 或负数没有意义 */
    private static final int MIN_DAYS = 1;

    private final LinkReportRepository linkReportRepository;
    private final LinkReportDailyRepository linkReportDailyRepository;
    private final AsnOrgRepository asnOrgRepository;
    private final LinkReportProperties properties;
    private final Clock clock;

    public AdminLinkHealthServiceImpl(LinkReportRepository linkReportRepository,
                                      LinkReportDailyRepository linkReportDailyRepository,
                                      AsnOrgRepository asnOrgRepository,
                                      LinkReportProperties properties, Clock clock) {
        this.linkReportRepository = linkReportRepository;
        this.linkReportDailyRepository = linkReportDailyRepository;
        this.asnOrgRepository = asnOrgRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public LinkHealthResponse getLinkHealth(int requestedDays) {
        int days = clampDays(requestedDays);
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(days));

        List<DomainAsnAggregate> aggregates = new ArrayList<>(
                linkReportRepository.aggregateGlobalByDomainAndAsn(from, now));
        // days 超过原始保留期时，from 之前那段只可能存在于按天聚合表——原始表早就被归档任务
        // 搬空了；days 落在保留期内时跳过这次查询，不多打一次库
        if (days > properties.getRawRetentionDays()) {
            LocalDate dailyFrom = LocalDate.ofInstant(from, ZoneOffset.UTC);
            LocalDate dailyTo = LocalDate.ofInstant(now, ZoneOffset.UTC);
            aggregates.addAll(linkReportDailyRepository.aggregateGlobalByDomainAndAsn(dailyFrom, dailyTo));
        }

        return new LinkHealthResponse(buildDomainRows(aggregates));
    }

    /** 收敛到 [MIN_DAYS, 按天聚合保留天数]——超过按天聚合保留天数的数据两张表都不会再有 */
    private int clampDays(int requestedDays) {
        int upperBound = properties.getDailyRetentionDays();
        return Math.min(Math.max(requestedDays, MIN_DAYS), upperBound);
    }

    /**
     * 按 (failureDomain, asn) 把两张表来的聚合行求和，再按 failureDomain 分组成 {@link DomainRow}。
     * 运营商维度的两种"未知"编码在这一步统一成空串——{@link LinkReportRepository.DomainAsnAggregate#asn()}
     * 的约定是 null 表示反查失败（{@link LinkReportDailyRepository} 已经把它自己表里的空串编码
     * 归一到这个约定），这里最后落到响应契约时再转成空串（响应契约的约定见
     * {@link LinkHealthResponse.AsnCell} 的类注释）。
     * <p>
     * 分组只认 <b>ASN</b>，展示名是事后贴上去的标签：上游对同一个 ASN 的文案会漂
     * （今天 China Telecom、明天 CHINANET-BACKBONE），让名字参与分组会把同一家运营商裂成两列。
     * 格子的排序则按<b>样本量倒序</b>（同数再按 ASN 串升序兜稳定），理由见下方 cells 那段注释。
     * 名字从 {@code asn_org} <b>一次读全表</b>（几十行量级）后在内存里按 ASN 取，不在循环里逐个查库；
     * 查不到就留 null（前端退回显示 ASN 串），<b>不以"有没有名字"决定这一列出不出</b>——
     * 少一列等于凭空丢掉一批真实流量。
     */
    private List<DomainRow> buildDomainRows(List<DomainAsnAggregate> aggregates) {
        record DomainAsnKey(String failureDomain, String asn) {
        }

        Map<DomainAsnKey, List<DomainAsnAggregate>> grouped = aggregates.stream()
                .collect(Collectors.groupingBy(agg ->
                        new DomainAsnKey(agg.failureDomain(), agg.asn() == null ? "" : agg.asn())));

        Map<String, String> orgNames = resolveOrgNames();

        // TreeMap 只是为了让同一次请求内两次调用给出一致的顺序，方便测试断言，不是业务要求
        Map<String, List<AsnCell>> cellsByDomain = new TreeMap<>();
        for (Map.Entry<DomainAsnKey, List<DomainAsnAggregate>> entry : grouped.entrySet()) {
            long samples = entry.getValue().stream().mapToLong(DomainAsnAggregate::samples).sum();
            long aliveCount = entry.getValue().stream().mapToLong(DomainAsnAggregate::aliveCount).sum();
            Double successRate = samples == 0 ? null : (double) aliveCount / samples;
            String asn = entry.getKey().asn();
            // 空串 asn（反查失败）在 asn_org 里永远查不到，orgName 恒为 null——这正是想要的：
            // 谁都不知道是哪家，自然没有名字，前端把这一格显示成「未知运营商」
            AsnCell cell = new AsnCell(asn, orgNames.get(asn), samples, aliveCount, successRate);
            cellsByDomain.computeIfAbsent(entry.getKey().failureDomain(), d -> new ArrayList<>()).add(cell);
        }

        List<DomainRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<AsnCell>> entry : cellsByDomain.entrySet()) {
            // 主序是样本量倒序：这张矩阵是用来看「哪家运营商出问题」的，目光该先落在流量最大的
            // 几家上——占大头的运营商成功率掉下去才是事故，一天只有几个样本的运营商 0% 多半是噪声。
            // 按 ASN 串字典序排会把 AS1 打头的小运营商顶到第一列。样本相同再按 ASN 串升序，
            // 只为让同一次请求两次调用给出稳定顺序（空串 asn 因此排在同数那几格最前），不是业务要求
            List<AsnCell> cells = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(AsnCell::samples).reversed()
                            .thenComparing(AsnCell::asn))
                    .toList();
            long domainSamples = cells.stream().mapToLong(AsnCell::samples).sum();
            long domainAlive = cells.stream().mapToLong(AsnCell::aliveCount).sum();
            long domainFailovers = grouped.entrySet().stream()
                    .filter(g -> g.getKey().failureDomain().equals(entry.getKey()))
                    .flatMap(g -> g.getValue().stream())
                    .mapToLong(DomainAsnAggregate::failovers)
                    .sum();
            rows.add(new DomainRow(entry.getKey(), domainSamples, domainAlive, domainFailovers, cells));
        }
        return rows;
    }

    /**
     * 查 ASN → 展示名快照；查询异常一律 fail-soft 返回空 Map，矩阵随之退回只显示 ASN 串。
     * <p>
     * 取舍与 {@link LinkReportAlertService#checkAll()} 那边完全一致：{@code asn_org} 存的是纯展示
     * 数据，而矩阵里的样本与成功率才是这个页面的全部价值。让它抛出去，一张<b>装饰用</b>的表出问题
     * （迁移没跑到、表权限不对）就会把整个链路健康页打成 500——恰恰是在需要看链路状况的时候
     * 什么都看不到。日志留着，故障并不会被藏起来。
     */
    private Map<String, String> resolveOrgNames() {
        try {
            return asnOrgRepository.findAllNames();
        } catch (Exception e) {
            log.warn("查询 ASN 展示名失败，本次链路健康矩阵只显示 ASN 串", e);
            return Map.of();
        }
    }

}
