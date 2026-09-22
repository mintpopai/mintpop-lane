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
    @DisplayName("展示名为 null 或空白时整条不写——org_name 是 NOT NULL，且写错了就再也盖不掉")
    void blankOrgNameIsNotWrittenAtAll() {
        // INSERT IGNORE 在严格模式下会把 null 悄悄写成空串：一旦落下去，「有则不动」意味着
        // 这个 ASN 的展示名被空串永久钉死，此后哪怕反查到了真名字也写不进来
        repository.insertIfAbsent("AS4134", null, NOW);
        repository.insertIfAbsent("AS4837", "   ", NOW);

        assertThat(repository.findAllNames()).isEmpty();

        // 挡掉之后，真名字来了还能正常记上
        repository.insertIfAbsent("AS4134", "China Telecom", NOW);
        assertThat(repository.findAllNames()).containsExactly(entry("AS4134", "China Telecom"));
    }

    @Test
    @DisplayName("展示名落库前由本层截到 64 字符并 trim，不把这件事留给 MySQL 去猜")
    void overlongOrgNameIsTruncatedBeforeInsert() {
        String longOrgName = "China Networks Inter-Exchange, China Telecommunications Corporation";
        assertThat(longOrgName.length()).isGreaterThan(64); // 这条样本确实超长

        repository.insertIfAbsent("AS4134", longOrgName, NOW);
        // 首尾空白这条才真正区分「我们截」与「MySQL 截」：INSERT IGNORE 下超长会被 MySQL
        // 静默砍到列宽（结果碰巧一样），但它不会替你 trim——空白原样留在表里，
        // 同一家运营商于是可能以「China Unicom」和「  China Unicom  」两个样子出现
        repository.insertIfAbsent("AS4837", "   China Unicom   ", NOW);

        assertThat(repository.findAllNames()).containsOnly(
                entry("AS4134", longOrgName.substring(0, 64)),
                entry("AS4837", "China Unicom"));
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
