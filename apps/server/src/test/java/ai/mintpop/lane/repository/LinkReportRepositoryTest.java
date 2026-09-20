package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkReportRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LinkReportRepository repository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;

    private Long userId;

    private static final Instant WINDOW_START = Instant.parse("2026-09-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();

        userId = fixtures.createUser("u1", null, null);
    }

    private LinkReport newReport(Long userId, String failureDomain, Instant windowStart,
                                     int samples, int aliveCount) {
        LinkReport report = new LinkReport();
        report.setUserId(userId);
        report.setFailureDomain(failureDomain);
        report.setWindowStart(windowStart);
        report.setSamples(samples);
        report.setAliveCount(aliveCount);
        report.setNoSampleCount(0);
        report.setFailovers(0);
        return report;
    }

    private long countRowsForUser(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM link_report WHERE user_id = ?", Long.class, userId);
    }

    @Test
    @DisplayName("同一窗口重复上报只留一行，且是覆盖不是累加")
    void repeatedUpsertOfSameWindowOverwrites() {
        LinkReport first = newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 9);
        repository.upsertWindow(first);

        LinkReport second = newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 3);
        repository.upsertWindow(second);

        assertThat(countRowsForUser(userId)).isEqualTo(1);

        List<LinkReportRepository.DomainIspAggregate> aggregates =
                repository.aggregateByDomainAndIsp(userId, WINDOW_START, WINDOW_START.plusSeconds(1));
        assertThat(aggregates).hasSize(1);
        assertThat(aggregates.get(0).aliveCount()).isEqualTo(3);
        assertThat(aggregates.get(0).samples()).isEqualTo(10);
    }

    @Test
    @DisplayName("故障域为空串与为具体值是两个不同的窗口，不会互相覆盖")
    void emptyFailureDomainIsItsOwnWindow() {
        LinkReport unresolved = newReport(userId, "", WINDOW_START, 5, 5);
        repository.upsertWindow(unresolved);

        LinkReport resolved = newReport(userId, "jp.tsdns.top", WINDOW_START, 5, 1);
        repository.upsertWindow(resolved);

        assertThat(countRowsForUser(userId)).isEqualTo(2);

        List<LinkReportRepository.DomainIspAggregate> aggregates =
                repository.aggregateByDomainAndIsp(userId, WINDOW_START, WINDOW_START.plusSeconds(1));
        assertThat(aggregates).hasSize(2);
        assertThat(aggregates.stream().map(LinkReportRepository.DomainIspAggregate::failureDomain))
                .containsExactlyInAnyOrder("", "jp.tsdns.top");
    }

    @Test
    @DisplayName("p50 可以从有值改回 null，updateStrategy 没被漏配")
    void p50CanBeClearedBackToNull() {
        LinkReport withLatency = newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 10);
        withLatency.setP50LatencyMs(180);
        repository.upsertWindow(withLatency);

        LinkReport withoutLatency = newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 0);
        withoutLatency.setP50LatencyMs(null);
        repository.upsertWindow(withoutLatency);

        Integer p50 = jdbc.queryForObject(
                "SELECT p50_latency_ms FROM link_report WHERE user_id = ? AND failure_domain = ?",
                Integer.class, userId, "jp.tsdns.top");
        assertThat(p50).isNull();
    }

    @Test
    @DisplayName("删用户级联删掉它的上报行")
    void deletingUserCascadesReports() {
        repository.upsertWindow(newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 10));
        assertThat(countRowsForUser(userId)).isEqualTo(1);

        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);

        assertThat(countRowsForUser(userId)).isZero();
    }

    @Test
    @DisplayName("findWindowsBefore 只返回窗口起点早于给定时刻的行，同一时刻及之后的不算")
    void findWindowsBeforeReturnsOnlyStrictlyOlderWindows() {
        repository.upsertWindow(newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 10));
        repository.upsertWindow(newReport(userId, "jp.tsdns.top", WINDOW_START.plusSeconds(300), 10, 10));

        List<LinkReport> before = repository.findWindowsBefore(WINDOW_START.plusSeconds(300));

        assertThat(before).hasSize(1);
        assertThat(before.get(0).getWindowStart()).isEqualTo(WINDOW_START);
    }

    @Test
    @DisplayName("deleteWindowsBefore 只删掉早于给定时刻的行，之后的保留")
    void deleteWindowsBeforeRemovesOnlyOlderRows() {
        repository.upsertWindow(newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 10));
        repository.upsertWindow(newReport(userId, "jp.tsdns.top", WINDOW_START.plusSeconds(300), 10, 10));

        repository.deleteWindowsBefore(WINDOW_START.plusSeconds(300));

        assertThat(countRowsForUser(userId)).isEqualTo(1);
        // 底层 jdbc.queryForObject 走的是 Spring 的 DATETIME->LocalDateTime 映射，不支持直接转 Instant；
        // 连接时区已钉死 UTC（见 MysqlTestBase），LocalDateTime 按 UTC 解释即还原出原始 Instant
        LocalDateTime remaining = jdbc.queryForObject(
                "SELECT window_start FROM link_report WHERE user_id = ?", LocalDateTime.class, userId);
        assertThat(remaining.toInstant(ZoneOffset.UTC)).isEqualTo(WINDOW_START.plusSeconds(300));
    }

    @Test
    @DisplayName("aggregateByDomainAndIsp 按故障域×运营商分组求和，且只统计给定区间与该用户")
    void aggregateByDomainAndIspSumsWithinRangePerUser() {
        Long otherUserId = fixtures.createUser("u2", null, null);

        LinkReport a1 = newReport(userId, "jp.tsdns.top", WINDOW_START, 10, 9);
        a1.setIsp("CTC");
        repository.upsertWindow(a1);

        LinkReport a2 = newReport(userId, "jp.tsdns.top", WINDOW_START.plusSeconds(300), 10, 5);
        a2.setIsp("CTC");
        repository.upsertWindow(a2);

        // 区间外，不该被计入
        LinkReport outOfRange = newReport(userId, "jp.tsdns.top", WINDOW_START.minusSeconds(300), 100, 100);
        outOfRange.setIsp("CTC");
        repository.upsertWindow(outOfRange);

        // 别的用户，不该被计入
        LinkReport otherUserReport = newReport(otherUserId, "jp.tsdns.top", WINDOW_START, 100, 100);
        otherUserReport.setIsp("CTC");
        repository.upsertWindow(otherUserReport);

        List<LinkReportRepository.DomainIspAggregate> aggregates = repository.aggregateByDomainAndIsp(
                userId, WINDOW_START, WINDOW_START.plusSeconds(301));

        assertThat(aggregates).hasSize(1);
        LinkReportRepository.DomainIspAggregate row = aggregates.get(0);
        assertThat(row.failureDomain()).isEqualTo("jp.tsdns.top");
        assertThat(row.isp()).isEqualTo("CTC");
        assertThat(row.samples()).isEqualTo(20);
        assertThat(row.aliveCount()).isEqualTo(14);
    }

    @Test
    @DisplayName("aggregateAllUsersByDomainAndIsp 在 SQL 层按用户×故障域×运营商分组求和，一次查出全部用户："
            + "3 条原始窗口只应合并成 2 行——行数是分组数，不是原始窗口数")
    void aggregateAllUsersByDomainAndIspGroupsAcrossUsersInSql() {
        Long userA = userId;
        Long userB = fixtures.createUser("u2", null, null);

        // userA：同一分组两条窗口，应合并成 1 行
        LinkReport a1 = newReport(userA, "jp.tsdns.top", WINDOW_START, 10, 9);
        a1.setIsp("CTC");
        repository.upsertWindow(a1);
        LinkReport a2 = newReport(userA, "jp.tsdns.top", WINDOW_START.plusSeconds(300), 10, 5);
        a2.setIsp("CTC");
        repository.upsertWindow(a2);

        // userB：不同用户、不同故障域，单独一行，不会与 userA 的合并到一起
        LinkReport b1 = newReport(userB, "us.tsdns.top", WINDOW_START, 20, 18);
        b1.setIsp("CUCC");
        repository.upsertWindow(b1);

        List<LinkReportRepository.UserDomainIspAggregate> aggregates = repository
                .aggregateAllUsersByDomainAndIsp(WINDOW_START, WINDOW_START.plusSeconds(301));

        // 3 条原始窗口，但只有 2 个「用户×故障域×运营商」分组：若实现退化成把原始行原样搬回来
        // （而不是真的在 SQL 层 GROUP BY），这里会看到 3 行而不是 2 行
        assertThat(aggregates).hasSize(2);

        LinkReportRepository.UserDomainIspAggregate userARow = aggregates.stream()
                .filter(row -> row.userId().equals(userA)).findFirst().orElseThrow();
        assertThat(userARow.failureDomain()).isEqualTo("jp.tsdns.top");
        assertThat(userARow.isp()).isEqualTo("CTC");
        assertThat(userARow.samples()).isEqualTo(20);
        assertThat(userARow.aliveCount()).isEqualTo(14);

        LinkReportRepository.UserDomainIspAggregate userBRow = aggregates.stream()
                .filter(row -> row.userId().equals(userB)).findFirst().orElseThrow();
        assertThat(userBRow.failureDomain()).isEqualTo("us.tsdns.top");
        assertThat(userBRow.isp()).isEqualTo("CUCC");
        assertThat(userBRow.samples()).isEqualTo(20);
        assertThat(userBRow.aliveCount()).isEqualTo(18);
    }
}
