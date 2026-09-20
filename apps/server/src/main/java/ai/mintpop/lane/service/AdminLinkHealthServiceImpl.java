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
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
@Service
public class AdminLinkHealthServiceImpl implements AdminLinkHealthService {

    /** days 参数下限：至少查 1 天，0 或负数没有意义 */
    private static final int MIN_DAYS = 1;

    private final LinkReportRepository linkReportRepository;
    private final LinkReportDailyRepository linkReportDailyRepository;
    private final EntryIpHistoryRepository entryIpHistoryRepository;
    private final LinkReportProperties properties;
    private final Clock clock;

    public AdminLinkHealthServiceImpl(LinkReportRepository linkReportRepository,
                                      LinkReportDailyRepository linkReportDailyRepository,
                                      EntryIpHistoryRepository entryIpHistoryRepository,
                                      LinkReportProperties properties, Clock clock) {
        this.linkReportRepository = linkReportRepository;
        this.linkReportDailyRepository = linkReportDailyRepository;
        this.entryIpHistoryRepository = entryIpHistoryRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public LinkHealthResponse getLinkHealth(int requestedDays) {
        int days = clampDays(requestedDays);
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofDays(days));

        List<DomainIspAggregate> aggregates = new ArrayList<>(
                linkReportRepository.aggregateGlobalByDomainAndIsp(from, now));
        // days 超过原始保留期时，from 之前那段只可能存在于按天聚合表——原始表早就被归档任务
        // 搬空了；days 落在保留期内时跳过这次查询，不多打一次库
        if (days > properties.getRawRetentionDays()) {
            LocalDate dailyFrom = LocalDate.ofInstant(from, ZoneOffset.UTC);
            LocalDate dailyTo = LocalDate.ofInstant(now, ZoneOffset.UTC);
            aggregates.addAll(linkReportDailyRepository.aggregateGlobalByDomainAndIsp(dailyFrom, dailyTo));
        }

        return new LinkHealthResponse(buildDomainRows(aggregates), buildEntryIpTimeline(from));
    }

    /** 收敛到 [MIN_DAYS, 按天聚合保留天数]——超过按天聚合保留天数的数据两张表都不会再有 */
    private int clampDays(int requestedDays) {
        int upperBound = properties.getDailyRetentionDays();
        return Math.min(Math.max(requestedDays, MIN_DAYS), upperBound);
    }

    /**
     * 按 (failureDomain, isp) 把两张表来的聚合行求和，再按 failureDomain 分组成 {@link DomainRow}。
     * isp 的两种"未知"编码在这一步统一成空串——{@link LinkReportRepository.DomainIspAggregate#isp()}
     * 的约定是 null 表示反查失败（{@link LinkReportDailyRepository} 已经把它自己表里的空串编码
     * 归一到这个约定），这里最后落到响应契约时再转成空串（响应契约的约定见
     * {@link LinkHealthResponse.IspCell} 的类注释）。
     */
    private List<DomainRow> buildDomainRows(List<DomainIspAggregate> aggregates) {
        record DomainIspKey(String failureDomain, String isp) {
        }

        Map<DomainIspKey, List<DomainIspAggregate>> grouped = aggregates.stream()
                .collect(Collectors.groupingBy(agg ->
                        new DomainIspKey(agg.failureDomain(), agg.isp() == null ? "" : agg.isp())));

        // TreeMap 只是为了让同一次请求内两次调用给出一致的顺序，方便测试断言，不是业务要求
        Map<String, List<IspCell>> cellsByDomain = new TreeMap<>();
        for (Map.Entry<DomainIspKey, List<DomainIspAggregate>> entry : grouped.entrySet()) {
            long samples = entry.getValue().stream().mapToLong(DomainIspAggregate::samples).sum();
            long aliveCount = entry.getValue().stream().mapToLong(DomainIspAggregate::aliveCount).sum();
            Double successRate = samples == 0 ? null : (double) aliveCount / samples;
            IspCell cell = new IspCell(entry.getKey().isp(), samples, aliveCount, successRate);
            cellsByDomain.computeIfAbsent(entry.getKey().failureDomain(), d -> new ArrayList<>()).add(cell);
        }

        List<DomainRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<IspCell>> entry : cellsByDomain.entrySet()) {
            List<IspCell> cells = entry.getValue().stream()
                    .sorted(Comparator.comparing(IspCell::isp))
                    .toList();
            long domainSamples = cells.stream().mapToLong(IspCell::samples).sum();
            long domainAlive = cells.stream().mapToLong(IspCell::aliveCount).sum();
            long domainFailovers = grouped.entrySet().stream()
                    .filter(g -> g.getKey().failureDomain().equals(entry.getKey()))
                    .flatMap(g -> g.getValue().stream())
                    .mapToLong(DomainIspAggregate::failovers)
                    .sum();
            rows.add(new DomainRow(entry.getKey(), domainSamples, domainAlive, domainFailovers, cells));
        }
        return rows;
    }

    /**
     * 把 {@code entry_ip_history} 里同一 (failureDomain, vantage) 分组内相邻两条记录之间的差异
     * 变成一次"变更事件"，再按 {@code changedAt >= from} 过滤——过滤必须在算出变更列表之后做，
     * 而不是下推到 repository 的时间范围查询，否则会看不到窗口之前的最后一条基线记录，
     * 把"进入查询窗口后的第一次变更"误判成"没有前值、不算变更"。
     */
    private List<EntryIpChange> buildEntryIpTimeline(Instant from) {
        record DomainVantageKey(String failureDomain, DnsVantage vantage) {
        }

        List<EntryIpHistory> all = entryIpHistoryRepository.findAllOrderByDomainVantageAndTime();
        Map<DomainVantageKey, List<EntryIpHistory>> grouped = new LinkedHashMap<>();
        for (EntryIpHistory history : all) {
            grouped.computeIfAbsent(new DomainVantageKey(history.getFailureDomain(), history.getVantage()),
                    key -> new ArrayList<>()).add(history);
        }

        List<EntryIpChange> changes = new ArrayList<>();
        for (Map.Entry<DomainVantageKey, List<EntryIpHistory>> entry : grouped.entrySet()) {
            List<EntryIpHistory> history = entry.getValue();
            for (int i = 1; i < history.size(); i++) {
                EntryIpHistory current = history.get(i);
                if (current.getObservedAt().isBefore(from)) {
                    continue;
                }
                EntryIpHistory previous = history.get(i - 1);
                changes.add(new EntryIpChange(entry.getKey().failureDomain(), entry.getKey().vantage().name(),
                        previous.getEntryIps(), current.getEntryIps(), current.getObservedAt()));
            }
        }
        return changes.stream()
                .sorted(Comparator.comparing(EntryIpChange::changedAt).reversed())
                .toList();
    }
}
