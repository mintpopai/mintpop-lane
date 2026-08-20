package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;
    private Long userId;

    @BeforeEach
    void 准备数据() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.清空();
        Long front = fixtures.建FRONT节点("FRONT-1");
        userId = fixtures.建用户("logto-u1", front, null);
    }

    @Test
    @DisplayName("凭据落库为密文，读回是明文")
    void 凭据落库为密文读回是明文() {
        Long id = fixtures.建订阅(userId, AgentType.CLAUDE, "Claude 席位 1",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "sk-ant-秘密");

        String raw = fixtures.读原始密文列("subscription", "credential_cipher", id);
        assertThat(raw).isNotBlank().doesNotContain("sk-ant-秘密");

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getCredential()).isEqualTo("sk-ant-秘密");
        assertThat(read.getAgentType()).isEqualTo(AgentType.CLAUDE);
        assertThat(read.getName()).isEqualTo("Claude 席位 1");
    }

    @Test
    @DisplayName("同一用户同一 agent 可并存多条订阅")
    void 同一用户同agent可并存多条() {
        fixtures.建订阅(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred-a");
        fixtures.建订阅(userId, AgentType.CLAUDE, "席位 B",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred-b");

        List<SubscriptionDto> list = subscriptionRepository.findByUserId(userId);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("在期判定：起含止不含")
    void 在期判定起含止不含() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        SubscriptionDto s = new SubscriptionDto();
        s.setStartsAt(now);
        s.setEndsAt(now.plus(1, ChronoUnit.DAYS));
        assertThat(s.isActiveAt(now)).isTrue();
        assertThat(s.isActiveAt(now.plus(1, ChronoUnit.DAYS))).isFalse();
        assertThat(s.isActiveAt(now.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("删用户级联删订阅")
    void 删用户级联删订阅() {
        Long id = fixtures.建订阅(userId, AgentType.CODEX, "Codex 席位",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        userRepository.deleteById(userId);
        assertThat(subscriptionRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("落库语义：写入的 Instant 在 DATETIME 列里的字面值就是 UTC 墙钟")
    void 落库字面值为UTC墙钟() {
        Long id = fixtures.建订阅(userId, AgentType.CLAUDE, "UTC 落库",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"), "cred");
        String literal = jdbc.queryForObject(
                "SELECT DATE_FORMAT(starts_at, '%Y-%m-%dT%H:%i:%s') FROM subscription WHERE id = ?",
                String.class, id);
        // 若 JVM 时区渗入编解码（driver 默认 connectionTimeZone=LOCAL），这里会差出本机时区的偏移
        assertThat(literal).isEqualTo("2026-08-01T00:00:00");
    }

    @Test
    @DisplayName("update 可延长止期并换凭据")
    void update可延长止期并换凭据() {
        Long id = fixtures.建订阅(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), "old");
        SubscriptionDto s = subscriptionRepository.findById(id).orElseThrow();
        s.setEndsAt(s.getEndsAt().plus(30, ChronoUnit.DAYS));
        s.setCredential("new");
        subscriptionRepository.update(s);

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getCredential()).isEqualTo("new");
        assertThat(read.getEndsAt()).isAfter(Instant.now().plus(20, ChronoUnit.DAYS));
    }
}
