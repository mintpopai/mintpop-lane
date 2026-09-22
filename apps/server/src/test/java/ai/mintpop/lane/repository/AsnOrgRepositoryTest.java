package ai.mintpop.lane.repository;

import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * ASN 到展示名的映射表。运营商维度以 ASN 做键、名字只做展示——本仓储只负责
 * 「首次见到某个 ASN 时把当时的展示名记下来」，此后名字不再改动，
 * 免得上游文案漂移（同一家运营商今天叫 China Telecom、明天叫 CHINANET-BACKBONE）
 * 把同一个 ASN 的历史裂成两段。
 */
class AsnOrgRepositoryTest extends MysqlTestBase {

    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AsnOrgRepository repository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void setUp() {
        new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository).clearAll();
    }

    @Test
    @DisplayName("同一 ASN 第二次 insertIfAbsent 不覆盖首次记下的展示名")
    void secondInsertKeepsFirstSeenName() {
        repository.insertIfAbsent("AS4134", "China Telecom", NOW);
        repository.insertIfAbsent("AS4134", "CHINANET-BACKBONE", NOW.plusSeconds(60));

        assertThat(repository.findAllNames()).containsExactly(entry("AS4134", "China Telecom"));
    }

    @Test
    @DisplayName("findAllNames 返回全表 asn -> org_name")
    void findAllNamesReturnsEveryRow() {
        repository.insertIfAbsent("AS4134", "China Telecom", NOW);
        repository.insertIfAbsent("AS4837", "China Unicom", NOW);

        assertThat(repository.findAllNames())
                .containsOnly(entry("AS4134", "China Telecom"), entry("AS4837", "China Unicom"));
    }

    @Test
    @DisplayName("表里没有任何映射时 findAllNames 返回空 Map，不是 null")
    void findAllNamesReturnsEmptyMapWhenTableIsEmpty() {
        assertThat(repository.findAllNames()).isEmpty();
    }

    @Test
    @DisplayName("首次见到的时间由调用方传入并原样落库——仓储不自己取「现在」")
    void firstSeenAtComesFromCaller() {
        repository.insertIfAbsent("AS4134", "China Telecom", NOW);

        Timestamp stored = jdbc.queryForObject(
                "SELECT first_seen_at FROM asn_org WHERE asn = ?", Timestamp.class, "AS4134");
        assertThat(stored).isNotNull();
        assertThat(stored.toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("第二次 insertIfAbsent 同样不刷新首次见到的时间")
    void secondInsertKeepsFirstSeenAt() {
        repository.insertIfAbsent("AS4134", "China Telecom", NOW);
        repository.insertIfAbsent("AS4134", "China Telecom", NOW.plusSeconds(3600));

        Timestamp stored = jdbc.queryForObject(
                "SELECT first_seen_at FROM asn_org WHERE asn = ?", Timestamp.class, "AS4134");
        assertThat(stored).isNotNull();
        assertThat(stored.toInstant()).isEqualTo(NOW);
    }
}
