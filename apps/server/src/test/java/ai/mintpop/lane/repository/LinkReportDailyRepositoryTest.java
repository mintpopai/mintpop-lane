package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LinkReportDailyRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private LinkReportDailyRepository repository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;
    private Long userId;

    private static final LocalDate DAY = LocalDate.parse("2026-09-01");

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("u1", null, null);
    }

    private LinkReportDaily newRow(Long userId, String failureDomain, String asn, LocalDate statDate,
                                   long samples, long aliveCount) {
        LinkReportDaily row = new LinkReportDaily();
        row.setUserId(userId);
        row.setFailureDomain(failureDomain);
        row.setAsn(asn);
        row.setStatDate(statDate);
        row.setSamples(samples);
        row.setAliveCount(aliveCount);
        row.setNoSampleCount(0L);
        row.setFailovers(0L);
        return row;
    }

    @Test
    @DisplayName("aggregateGlobalByDomainAndIsp 连用户维度也在 SQL 层求和掉，行数是分组数")
    void aggregateGlobalByDomainAndIspGroupsAcrossUsers() {
        Long userA = userId;
        Long userB = fixtures.createUser("u2", null, null);

        repository.upsertDay(newRow(userA, "jp.tsdns.top", "AS4134", DAY, 100, 90));
        repository.upsertDay(newRow(userB, "jp.tsdns.top", "AS4134", DAY, 50, 40));
        repository.upsertDay(newRow(userB, "us.tsdns.top", "AS4837", DAY, 10, 10));
        // 区间外，不该被计入
        repository.upsertDay(newRow(userA, "jp.tsdns.top", "AS4134", DAY.minusDays(30), 999, 999));

        List<LinkReportRepository.DomainIspAggregate> aggregates =
                repository.aggregateGlobalByDomainAndIsp(DAY, DAY);

        assertThat(aggregates).hasSize(2);
        var jpRow = aggregates.stream().filter(a -> a.failureDomain().equals("jp.tsdns.top")).findFirst()
                .orElseThrow();
        assertThat(jpRow.isp()).isEqualTo("AS4134");
        assertThat(jpRow.samples()).isEqualTo(150);
        assertThat(jpRow.aliveCount()).isEqualTo(130);
    }

    @Test
    @DisplayName("asn 的空串编码（反查失败）在这一层被归一化成 null，对齐 link_report.source_asn 的编码")
    void unresolvedAsnIsNormalizedToNull() {
        repository.upsertDay(newRow(userId, "jp.tsdns.top", "", DAY, 20, 15));

        List<LinkReportRepository.DomainIspAggregate> aggregates =
                repository.aggregateGlobalByDomainAndIsp(DAY, DAY);

        assertThat(aggregates).hasSize(1);
        assertThat(aggregates.get(0).isp()).isNull();
    }
}
