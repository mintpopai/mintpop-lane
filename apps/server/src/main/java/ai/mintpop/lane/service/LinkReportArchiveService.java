package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.repository.LinkReportDailyRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 归档定时任务：把超过 {@link LinkReportProperties#getRawRetentionDays()} 的原始窗口
 * （link_report）压成按天聚合（link_report_daily），并清理超过
 * {@link LinkReportProperties#getDailyRetentionDays()} 的按天聚合行。
 * <p>
 * 按「用户 × 故障域 × 运营商 × 统计日」分组，把这一维度<b>当前仍存在</b>的原始窗口重新求和，
 * 与该维度<b>已有的按天聚合行</b>相加后整行覆盖写入，再删除这批原始窗口——归档与删除同一事务。
 * <p>
 * 与已有行相加（而不是只用本轮批次覆盖）是必须的：归档周期通常比一整天短，
 * 同一统计日的窗口会跨多轮陆续越过保留期线——第一轮只处理当天前半段窗口，
 * 第二轮 cutoff 往后推才轮到后半段。若整行覆盖只写"本轮批次"的和，会把上一轮已经写入、
 * 且原始数据已被删除、无法找回的前半段总数覆盖丢失。见
 * {@code LinkReportArchiveServiceTest#sameStatDateArchivedAcrossTwoRunsAccumulates}。
 * <p>
 * 幂等性由"原始窗口处理完即在同一事务内删除"保证：同一批原始窗口不会被处理第二次，
 * 因此"读现有行 + 本轮批次相加"不会产生重复计入——不能用
 * {@code INSERT ... ON DUPLICATE KEY UPDATE samples = samples + VALUES(samples)}
 * 让数据库自己做累加，那样如果归档任务因异常重跑、又恰好没能删掉上一次已处理的原始窗口，
 * 会把同一批数据再加一遍；这里的加法只发生在 Java 侧、只加"当前查到的原始窗口"这一份。
 * <p>
 * {@code failureDomain} 两表同为空串编码，不需要转换；{@code isp} 编码不同——
 * {@link LinkReport#getIsp()} 是 null 编码，{@link LinkReportDaily#getIsp()} 是
 * {@code NOT NULL DEFAULT ''} 且进了唯一键，归档时必须把 null 转成空串。
 * <p>
 * 按天聚合刻意不带 p50 延迟——中位数不可跨窗口相加，见 {@link LinkReportDaily} 类注释。
 */
@Slf4j
@Service
public class LinkReportArchiveService {

    private final LinkReportRepository linkReportRepository;
    private final LinkReportDailyRepository dailyRepository;
    private final LinkReportProperties properties;
    private final Clock clock;

    public LinkReportArchiveService(LinkReportRepository linkReportRepository,
                                    LinkReportDailyRepository dailyRepository,
                                    LinkReportProperties properties, Clock clock) {
        this.linkReportRepository = linkReportRepository;
        this.dailyRepository = dailyRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * fixedDelay：上一轮跑完再计时；initialDelay 同样取周期，避免每次重启都立刻扫一遍，
     * 形态照抄 {@link EntryIpWatchService}。
     */
    @Scheduled(fixedDelayString = "#{@linkReportProperties.archiveInterval.toMillis()}",
            initialDelayString = "#{@linkReportProperties.archiveInterval.toMillis()}")
    @Transactional
    public void archive() {
        Instant now = clock.instant();

        Instant rawCutoff = now.minus(Duration.ofDays(properties.getRawRetentionDays()));
        archiveOldWindows(rawCutoff);

        LocalDate dailyCutoff = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(properties.getDailyRetentionDays());
        dailyRepository.deleteBefore(dailyCutoff);
    }

    private void archiveOldWindows(Instant rawCutoff) {
        List<LinkReport> oldWindows = linkReportRepository.findWindowsBefore(rawCutoff);
        if (oldWindows.isEmpty()) {
            return;
        }

        Map<DailyKey, List<LinkReport>> grouped = oldWindows.stream()
                .collect(Collectors.groupingBy(this::keyOf));
        grouped.forEach(this::foldIntoDailyRow);

        // 全部分组都写完再删，与写入同一事务：崩在中间会整体回滚，不会出现"删了没写"或"写了没删"
        linkReportRepository.deleteWindowsBefore(rawCutoff);
    }

    private void foldIntoDailyRow(DailyKey key, List<LinkReport> windows) {
        long samples = windows.stream().mapToLong(LinkReport::getSamples).sum();
        long aliveCount = windows.stream().mapToLong(LinkReport::getAliveCount).sum();
        long noSampleCount = windows.stream().mapToLong(LinkReport::getNoSampleCount).sum();
        long failovers = windows.stream().mapToLong(LinkReport::getFailovers).sum();

        LinkReportDaily row = dailyRepository
                .find(key.userId(), key.failureDomain(), key.isp(), key.statDate())
                .orElseGet(LinkReportDaily::new);
        row.setUserId(key.userId());
        row.setFailureDomain(key.failureDomain());
        row.setIsp(key.isp());
        row.setStatDate(key.statDate());
        row.setSamples(orZero(row.getSamples()) + samples);
        row.setAliveCount(orZero(row.getAliveCount()) + aliveCount);
        row.setNoSampleCount(orZero(row.getNoSampleCount()) + noSampleCount);
        row.setFailovers(orZero(row.getFailovers()) + failovers);
        dailyRepository.upsertDay(row);
    }

    /**
     * 按天聚合的分组键。{@code isp} 在这里已经转换成空串编码——
     * {@code link_report.isp} 是 null 编码，{@code link_report_daily.isp} 是
     * {@code NOT NULL DEFAULT ''} 且进唯一键，漏转会在非空约束上炸。
     */
    private DailyKey keyOf(LinkReport report) {
        LocalDate statDate = LocalDate.ofInstant(report.getWindowStart(), ZoneOffset.UTC);
        String isp = report.getIsp() == null ? "" : report.getIsp();
        return new DailyKey(report.getUserId(), report.getFailureDomain(), isp, statDate);
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    /** 归档分组用的复合键：用 record 而不是拼字符串，天然正确处理 isp 已转换后不再为 null 的等值比较 */
    private record DailyKey(Long userId, String failureDomain, String isp, LocalDate statDate) {
    }
}
