package ai.mintpop.lane.repository;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;
    private Long userId;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("logto-u1", null);
    }

    @Test
    @DisplayName("凭据落库为密文，读回是明文")
    void credentialStoredAsCipherReadBackPlain() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "Claude 席位 1",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "sk-ant-秘密");

        String raw = fixtures.readRawCipherColumn("subscription", "credential_cipher", id);
        assertThat(raw).isNotBlank().doesNotContain("sk-ant-秘密");

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getCredential()).isEqualTo("sk-ant-秘密");
        assertThat(read.getAgentType()).isEqualTo(AgentType.CLAUDE);
        assertThat(read.getName()).isEqualTo("Claude 席位 1");
    }

    @Test
    @DisplayName("同一用户同一 agent 可并存多条订阅")
    void sameUserSameAgentAllowsMultiple() {
        fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred-a");
        fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 B",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred-b");

        List<SubscriptionDto> list = subscriptionRepository.findByUserId(userId);
        assertThat(list).hasSize(2);
    }

    @Test
    @DisplayName("在期判定：起含止不含")
    void activePeriodStartInclusiveEndExclusive() {
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
    void deleteUserCascadesSubscriptions() {
        Long id = fixtures.createSubscription(userId, AgentType.CODEX, "Codex 席位",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        userRepository.deleteById(userId);
        assertThat(subscriptionRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("落库语义：写入的 Instant 在 DATETIME 列里的字面值就是 UTC 墙钟")
    void storedLiteralIsUtcWallClock() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "UTC 落库",
                Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"), "cred");
        String literal = jdbc.queryForObject(
                "SELECT DATE_FORMAT(starts_at, '%Y-%m-%dT%H:%i:%s') FROM subscription WHERE id = ?",
                String.class, id);
        // 若 JVM 时区渗入编解码（driver 默认 connectionTimeZone=LOCAL），这里会差出本机时区的偏移
        assertThat(literal).isEqualTo("2026-08-01T00:00:00");
    }

    @Test
    @DisplayName("update 可延长止期并换凭据")
    void updateExtendsEndAndReplacesCredential() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(1, ChronoUnit.DAYS), "old");
        SubscriptionDto s = subscriptionRepository.findById(id).orElseThrow();
        s.setEndsAt(s.getEndsAt().plus(30, ChronoUnit.DAYS));
        s.setCredential("new");
        subscriptionRepository.update(s);

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getCredential()).isEqualTo("new");
        assertThat(read.getEndsAt()).isAfter(Instant.now().plus(20, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("bindDeviceIfUnbound 在未绑定时生效并写入绑定列")
    void bindDeviceIfUnboundSucceedsWhenUnbound() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        Instant boundAt = Instant.parse("2026-09-01T00:00:00Z");

        assertThat(subscriptionRepository.bindDeviceIfUnbound(id, 1L, boundAt)).isTrue();

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getBoundDeviceId()).isEqualTo(1L);
        assertThat(read.getBoundAt()).isEqualTo(boundAt);
    }

    @Test
    @DisplayName("bindDeviceIfUnbound 已绑定时不生效，且不覆盖原有绑定——这是防止第二台设备顶替绑定的核心安全属性")
    void bindDeviceIfUnboundFailsWhenAlreadyBoundAndLeavesBindingUntouched() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        Instant firstBoundAt = Instant.parse("2026-09-01T00:00:00Z");
        assertThat(subscriptionRepository.bindDeviceIfUnbound(id, 1L, firstBoundAt)).isTrue();

        Instant secondAttemptAt = Instant.parse("2026-09-02T00:00:00Z");
        assertThat(subscriptionRepository.bindDeviceIfUnbound(id, 2L, secondAttemptAt)).isFalse();

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getBoundDeviceId()).isEqualTo(1L);
        assertThat(read.getBoundAt()).isEqualTo(firstBoundAt);
    }

    @Test
    @DisplayName("rebindDevice 无条件覆盖原有绑定")
    void rebindDeviceOverwritesExistingBinding() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        subscriptionRepository.bindDeviceIfUnbound(id, 1L, Instant.parse("2026-09-01T00:00:00Z"));

        Instant rebindAt = Instant.parse("2026-09-10T00:00:00Z");
        subscriptionRepository.rebindDevice(id, 2L, rebindAt);

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getBoundDeviceId()).isEqualTo(2L);
        assertThat(read.getBoundAt()).isEqualTo(rebindAt);
    }

    @Test
    @DisplayName("unbindDevice 把绑定设备与绑定时刻两列一起清空")
    void unbindDeviceClearsBothColumns() {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        subscriptionRepository.bindDeviceIfUnbound(id, 1L, Instant.parse("2026-09-01T00:00:00Z"));

        subscriptionRepository.unbindDevice(id);

        SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
        assertThat(read.getBoundDeviceId()).isNull();
        assertThat(read.getBoundAt()).isNull();
    }

    @Test
    @DisplayName("bindDeviceIfUnbound 并发安全：两台设备真并发抢绑同一份未绑定订阅，恰好一个成功，且落库的正是那个赢家")
    void bindDeviceIfUnboundIsSafeUnderConcurrentAttempts() throws Exception {
        Long id = fixtures.createSubscription(userId, AgentType.CLAUDE, "席位 A",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        long deviceA = 201L;
        long deviceB = 202L;
        Instant now = Instant.parse("2026-09-01T00:00:00Z");

        // 两个线程各自独立调用注入的 subscriptionRepository bean——MyBatis 的 SqlSessionTemplate
        // 是线程安全的，每次调用都从连接池各取一条连接，两个线程因此真的是两条独立数据库连接在竞争同一行，
        // 不是同一条连接顺序执行。本测试类（以及 MysqlTestBase 全体既有用例）都没有 @Transactional，
        // 每次仓储调用都是各自 autocommit 的独立语句，没有测试事务把两个线程困在一起或彼此隔离的问题。
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> attemptA = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return subscriptionRepository.bindDeviceIfUnbound(id, deviceA, now);
            });
            Future<Boolean> attemptB = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return subscriptionRepository.bindDeviceIfUnbound(id, deviceB, now);
            });

            boolean resultA = attemptA.get(10, TimeUnit.SECONDS);
            boolean resultB = attemptB.get(10, TimeUnit.SECONDS);

            // 只断言「一真一假」还不够：还要证明库里最终落的绑定，就是返回 true 的那个线程绑的设备
            assertThat(List.of(resultA, resultB)).containsExactlyInAnyOrder(true, false);
            long winnerDeviceId = resultA ? deviceA : deviceB;

            SubscriptionDto read = subscriptionRepository.findById(id).orElseThrow();
            assertThat(read.getBoundDeviceId()).isEqualTo(winnerDeviceId);
        } finally {
            executor.shutdownNow();
        }
    }
}
