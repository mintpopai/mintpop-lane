package ai.mintpop.lane.service;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.repository.LinkReportDailyRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 归档定时任务：原始窗口（保留 7 天）压成按天聚合（保留 90 天）。
 * <p>
 * 幂等性依赖「归档 + 删除同一事务」：见 {@link #archiveIsIdempotent()} 与
 * {@link #sameStatDateArchivedAcrossTwoRunsAccumulates()}——后者额外验证了一个简报没明说、
 * 但按当前 schema 必然发生的场景：归档周期通常比一天短，同一统计日的窗口会跨多轮陆续越过
 * 保留期线，第二轮必须与第一轮已写入的聚合行相加，而不是用本轮批次整行覆盖丢掉第一轮的数据。
 */
class LinkReportArchiveServiceTest extends MysqlTestBase {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private static final String DOMAIN = "jp.tsdns.top";
    private static final String ASN = "AS4134";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired
    private LinkReportRepository linkReportRepository;
    @Autowired
    private LinkReportDailyRepository dailyRepository;
    @Autowired
    private ProxyNodeRepository nodeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;
    private Long userId;
    private LinkReportProperties properties;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("u1", null);
        properties = new LinkReportProperties(); // 默认 rawRetentionDays=7、dailyRetentionDays=90
    }

    private LinkReportArchiveService newService(Instant now) {
        return new LinkReportArchiveService(linkReportRepository, dailyRepository, properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void insertWindow(String domain, String asn, Instant windowStart, int samples, int alive) {
        insertWindow(userId, domain, asn, windowStart, samples, alive);
    }

    private void insertWindow(Long userId, String domain, String asn, Instant windowStart, int samples, int alive) {
        LinkReport report = new LinkReport();
        report.setUserId(userId);
        report.setFailureDomain(domain);
        report.setWindowStart(windowStart);
        report.setSamples(samples);
        report.setAliveCount(alive);
        report.setNoSampleCount(0);
        report.setFailovers(0);
        report.setSourceAsn(asn);
        linkReportRepository.upsertWindow(report);
    }

    private long countRawWindows() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM link_report WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    private LinkReportDaily newDailyRow(String domain, String asn, LocalDate statDate, long samples, long alive) {
        LinkReportDaily row = new LinkReportDaily();
        row.setUserId(userId);
        row.setFailureDomain(domain);
        row.setAsn(asn);
        row.setStatDate(statDate);
        row.setSamples(samples);
        row.setAliveCount(alive);
        row.setNoSampleCount(0L);
        row.setFailovers(0L);
        return row;
    }

    @Test
    @DisplayName("超过保留期的原始窗口被压成按天聚合，计数逐项相加")
    void oldWindowsAreFoldedIntoDailyRows() {
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));
        insertWindow(DOMAIN, ASN, eightDaysAgo, 10, 9);
        insertWindow(DOMAIN, ASN, eightDaysAgo.plusSeconds(300), 10, 8);
        insertWindow(DOMAIN, ASN, eightDaysAgo.plusSeconds(600), 10, 10);

        newService(NOW).archive();

        LinkReportDaily row = dailyRepository
                .find(userId, DOMAIN, ASN, LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC))
                .orElseThrow();
        assertThat(row.getSamples()).isEqualTo(30L);
        assertThat(row.getAliveCount()).isEqualTo(27L);
        assertThat(countRawWindows()).isZero();
    }

    @Test
    @DisplayName("按天聚合不带 p50 延迟——中位数不能跨窗口相加")
    void dailyRowsCarryNoMedianLatency() {
        // 结构性断言：LinkReportDaily 干脆不设这一列（与 link_report_daily 的表注释同源）。
        // 若将来有人"顺手"想把各窗口 p50 求平均当成当日 p50、给实体加一个字段，
        // 这条测试会因为反射扫到新字段而变红（已用临时加回 p50LatencyMs 字段的方式实测验证过）
        boolean hasLatencyField = Arrays.stream(LinkReportDaily.class.getDeclaredFields())
                .map(Field::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.contains("p50") || name.contains("latency"));
        assertThat(hasLatencyField).isFalse();

        // 行为性断言：窗口带着悬殊的 p50 值混进同一天，不影响其它数值列的正确求和
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));
        insertWindow(DOMAIN, ASN, eightDaysAgo, 10, 10);
        insertWindow(DOMAIN, ASN, eightDaysAgo.plusSeconds(300), 10, 5);

        newService(NOW).archive();

        LinkReportDaily row = dailyRepository
                .find(userId, DOMAIN, ASN, LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC))
                .orElseThrow();
        assertThat(row.getSamples()).isEqualTo(20L);
        assertThat(row.getAliveCount()).isEqualTo(15L);
    }

    @Test
    @DisplayName("保留期内的窗口一行都不动")
    void windowsWithinRetentionAreUntouched() {
        Instant withinRetention = NOW.minus(Duration.ofDays(3)); // < rawRetentionDays(7)
        insertWindow(DOMAIN, ASN, withinRetention, 10, 9);

        newService(NOW).archive();

        assertThat(countRawWindows()).isEqualTo(1);
        assertThat(dailyRepository.find(userId, DOMAIN, ASN, LocalDate.ofInstant(withinRetention, ZoneOffset.UTC)))
                .isEmpty();
    }

    @Test
    @DisplayName("归档可重复执行，跑两遍结果与跑一遍相同")
    void archiveIsIdempotent() {
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));
        insertWindow(DOMAIN, ASN, eightDaysAgo, 10, 9);
        insertWindow(DOMAIN, ASN, eightDaysAgo.plusSeconds(300), 10, 8);

        LinkReportArchiveService service = newService(NOW);
        service.archive();

        LinkReportDaily afterFirstRun = dailyRepository
                .find(userId, DOMAIN, ASN, LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC))
                .orElseThrow();
        assertThat(afterFirstRun.getSamples()).isEqualTo(20L);
        assertThat(afterFirstRun.getAliveCount()).isEqualTo(17L);

        service.archive(); // 第二遍：原始窗口已在第一遍删除，不该被重新计入

        LinkReportDaily afterSecondRun = dailyRepository
                .find(userId, DOMAIN, ASN, LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC))
                .orElseThrow();
        assertThat(afterSecondRun.getSamples()).isEqualTo(20L);
        assertThat(afterSecondRun.getAliveCount()).isEqualTo(17L);
        assertThat(countRawWindows()).isZero();
    }

    @Test
    @DisplayName("同一统计日的窗口分两轮先后越过保留期，两轮归档的总数会累加，不会被后一轮覆盖丢失")
    void sameStatDateArchivedAcrossTwoRunsAccumulates() {
        // 归档周期通常比一天短：同一天的窗口不会同一轮全部越过 7 天保留期线，
        // 第一轮只处理当天前半段（earlyWindow），第二轮 cutoff 往后推才轮到后半段（lateWindow）。
        // 若归档只用"本轮批次"整行覆盖，第二轮会用只含 lateWindow 的总数
        // 覆盖掉第一轮已写入、且原始行已删除、无法找回的 earlyWindow 总数
        LocalDate day = LocalDate.parse("2026-09-01");
        Instant earlyWindow = day.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant lateWindow = earlyWindow.plus(Duration.ofHours(18));

        insertWindow(DOMAIN, ASN, earlyWindow, 10, 9);
        insertWindow(DOMAIN, ASN, lateWindow, 10, 7);

        // t1 的 cutoff 落在 earlyWindow 与 lateWindow 之间：只有 earlyWindow 越线
        Instant t1 = earlyWindow.plus(Duration.ofDays(7)).plus(Duration.ofHours(6));
        newService(t1).archive();

        LinkReportDaily afterFirstRun = dailyRepository.find(userId, DOMAIN, ASN, day).orElseThrow();
        assertThat(afterFirstRun.getSamples()).isEqualTo(10L);
        assertThat(afterFirstRun.getAliveCount()).isEqualTo(9L);
        assertThat(countRawWindows()).isEqualTo(1); // lateWindow 还在保留期内，未被动

        // t2 的 cutoff 越过 lateWindow，第二轮归档
        Instant t2 = lateWindow.plus(Duration.ofDays(7)).plus(Duration.ofHours(1));
        newService(t2).archive();

        LinkReportDaily afterSecondRun = dailyRepository.find(userId, DOMAIN, ASN, day).orElseThrow();
        assertThat(afterSecondRun.getSamples()).isEqualTo(20L); // 10 + 10，不是只剩 lateWindow 的 10
        assertThat(afterSecondRun.getAliveCount()).isEqualTo(16L); // 9 + 7
        assertThat(countRawWindows()).isZero();
    }

    @Test
    @DisplayName("超过按天保留期的聚合行被删除")
    void expiredDailyRowsAreDeleted() {
        LocalDate expiredDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(200); // 远超 90 天保留期
        LocalDate recentDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(1);

        dailyRepository.upsertDay(newDailyRow(DOMAIN, ASN, expiredDate, 10, 9));
        dailyRepository.upsertDay(newDailyRow(DOMAIN, ASN, recentDate, 20, 18));

        newService(NOW).archive();

        assertThat(dailyRepository.find(userId, DOMAIN, ASN, expiredDate)).isEmpty();
        assertThat(dailyRepository.find(userId, DOMAIN, ASN, recentDate)).isPresent();
    }

    @Test
    @DisplayName("原始窗口 source_asn 为 null（反查失败）时，归档转成空串写入按天聚合，不违反非空约束")
    void nullAsnIsConvertedToEmptyStringWhenArchived() {
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));
        insertWindow(DOMAIN, null, eightDaysAgo, 10, 9);

        newService(NOW).archive();

        LinkReportDaily row = dailyRepository
                .find(userId, DOMAIN, "", LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC))
                .orElseThrow();
        assertThat(row.getSamples()).isEqualTo(10L);
        assertThat(row.getAsn()).isEqualTo("");
    }

    @Test
    @DisplayName("同一天里反查成功与反查失败的窗口各归各的：按天表两行，asn 分别是 AS4134 与空串")
    void resolvedAndUnresolvedAsnOfSameDayGoToSeparateDailyRows() {
        // 两种编码在同一天相遇是最容易被写错的地方：若归档把 null 和 "AS4134" 当成同一个键
        // （比如分组时统一转成空串再分组，或干脆拿 null 当通配），两段样本会被合成一行，
        // 「哪家运营商在劣化」的信号当场糊掉
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));
        insertWindow(DOMAIN, ASN, eightDaysAgo, 10, 9);
        insertWindow(DOMAIN, null, eightDaysAgo.plusSeconds(300), 20, 12);

        newService(NOW).archive();

        LocalDate statDate = LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC);
        LinkReportDaily resolved = dailyRepository.find(userId, DOMAIN, ASN, statDate).orElseThrow();
        assertThat(resolved.getSamples()).isEqualTo(10L);
        assertThat(resolved.getAliveCount()).isEqualTo(9L);

        LinkReportDaily unresolved = dailyRepository.find(userId, DOMAIN, "", statDate).orElseThrow();
        assertThat(unresolved.getAsn()).isEqualTo(""); // link_report 的 null 编码 → 本表的空串编码
        assertThat(unresolved.getSamples()).isEqualTo(20L);
        assertThat(unresolved.getAliveCount()).isEqualTo(12L);

        Long dailyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM link_report_daily WHERE user_id = ?", Long.class, userId);
        assertThat(dailyRows).isEqualTo(2);
    }

    @Test
    @DisplayName("不同用户/故障域/ASN 各自独立归档，不会互相合并")
    void differentDimensionsAreArchivedSeparately() {
        Long otherUserId = fixtures.createUser("u2", null);
        Instant eightDaysAgo = NOW.minus(Duration.ofDays(8));

        insertWindow(userId, DOMAIN, ASN, eightDaysAgo, 10, 9);
        insertWindow(otherUserId, DOMAIN, ASN, eightDaysAgo, 20, 18);
        insertWindow(userId, "us.tsdns.top", ASN, eightDaysAgo, 5, 5);
        // link_report 的真实唯一键是 (user_id, failure_domain, window_start)，不含 source_asn——
        // 一个窗口只对应一次探测、一个已解析的 ASN。这一行要与第一行同 user/domain 但不同 ASN，
        // 必须换一个不同的 windowStart，否则会撞上第一行的唯一键，被 upsertWindow 整行覆盖掉
        // （这是 upsertWindow 的正确行为，覆盖同一窗口的重复上报；踩坑记录见 task-4-report.md）
        insertWindow(userId, DOMAIN, "AS4837", eightDaysAgo.plusSeconds(900), 7, 6);

        newService(NOW).archive();

        LocalDate statDate = LocalDate.ofInstant(eightDaysAgo, ZoneOffset.UTC);
        assertThat(dailyRepository.find(userId, DOMAIN, ASN, statDate).orElseThrow().getSamples()).isEqualTo(10L);
        assertThat(dailyRepository.find(otherUserId, DOMAIN, ASN, statDate).orElseThrow().getSamples())
                .isEqualTo(20L);
        assertThat(dailyRepository.find(userId, "us.tsdns.top", ASN, statDate).orElseThrow().getSamples())
                .isEqualTo(5L);
        assertThat(dailyRepository.find(userId, DOMAIN, "AS4837", statDate).orElseThrow().getSamples())
                .isEqualTo(7L);
    }
}
