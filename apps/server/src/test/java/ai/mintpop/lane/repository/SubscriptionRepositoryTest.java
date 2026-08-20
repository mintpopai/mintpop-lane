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

import java.time.LocalDateTime;
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
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), "sk-ant-秘密");

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
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), "cred-a");
        fixtures.建订阅(userId, AgentType.CLAUDE, "席位 B",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), "cred-b");

        List<SubscriptionDto> list = subscriptionRepository.findByUserId(userId);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("在期判定：起含止不含")
    void 在期判定起含止不含() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 19, 12, 0);
        SubscriptionDto s = new SubscriptionDto();
        s.setStartsAt(now);
        s.setEndsAt(now.plusDays(1));
        assertThat(s.isActiveAt(now)).isTrue();
        assertThat(s.isActiveAt(now.plusDays(1))).isFalse();
        assertThat(s.isActiveAt(now.minusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("删用户级联删订阅")
    void 删用户级联删订阅() {
        Long id = fixtures.建订阅(userId, AgentType.CODEX, "Codex 席位",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), "cred");
        userRepository.deleteById(userId);
        assertThat(subscriptionRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("update 可延长止期并换凭据")
    void update可延长止期并换凭据() {
        Long id = fixtures.建订阅(userId, AgentType.CLAUDE, "席位 A",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), "old");
        SubscriptionDto s = subscriptionRepository.findById(id).orElseThrow();
        s.setEndsAt(s.getEndsAt().plusDays(30));
        s.setCredential("new");
        subscriptionRepository.update(s);

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getCredential()).isEqualTo("new");
        assertThat(read.getEndsAt()).isAfter(LocalDateTime.now().plusDays(20));
    }
}
