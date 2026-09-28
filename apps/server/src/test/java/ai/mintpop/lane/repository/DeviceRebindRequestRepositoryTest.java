package ai.mintpop.lane.repository;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import ai.mintpop.lane.util.RebindRequestNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceRebindRequestRepositoryTest extends MysqlTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserDeviceRepository deviceRepository;
    @Autowired private DeviceRebindRequestRepository requestRepository;

    private DatabaseFixtures fixtures;
    private Long userId;
    private Long subscriptionId;
    private Long fromDeviceId;
    private Long toDeviceId;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("logto-rebind-owner", null, null);
        subscriptionId = fixtures.createSubscription(userId, AgentType.CLAUDE, "Claude 席位",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        fromDeviceId = deviceRepository.upsert(userId, "device-old", "旧机器", "macos 26", "", now).getId();
        toDeviceId = deviceRepository.upsert(userId, "device-new", "新机器", "macos 26", "", now).getId();
    }

    private DeviceRebindRequest pending(Long subId, Long userId, Long fromId, Long toId) {
        DeviceRebindRequest r = new DeviceRebindRequest();
        r.setRequestNo(RebindRequestNo.generate(Instant.now()));
        r.setSubscriptionId(subId);
        r.setUserId(userId);
        r.setFromDeviceId(fromId);
        r.setToDeviceId(toId);
        r.setReason("换了新电脑");
        r.setStatus(RebindRequestStatus.PENDING);
        Long id = requestRepository.create(r);
        r.setId(id);
        return r;
    }

    @Test
    @DisplayName("create 后可用 findById 读回，初始状态为 PENDING")
    void createAndFindById() {
        DeviceRebindRequest created = pending(subscriptionId, userId, fromDeviceId, toDeviceId);

        DeviceRebindRequest read = requestRepository.findById(created.getId()).orElseThrow();
        assertThat(read.getRequestNo()).isEqualTo(created.getRequestNo());
        assertThat(read.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(read.getUserId()).isEqualTo(userId);
        assertThat(read.getFromDeviceId()).isEqualTo(fromDeviceId);
        assertThat(read.getToDeviceId()).isEqualTo(toDeviceId);
        assertThat(read.getReason()).isEqualTo("换了新电脑");
        assertThat(read.getStatus()).isEqualTo(RebindRequestStatus.PENDING);
        assertThat(read.getDecidedBy()).isNull();
        assertThat(read.getDecidedAt()).isNull();

        assertThat(requestRepository.findById(999_999L)).isEmpty();
    }

    @Test
    @DisplayName("findByStatus 与 findAll 均按创建时间倒序（新的在前）")
    void findByStatusAndFindAllOrdering() {
        DeviceRebindRequest r1 = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        DeviceRebindRequest r2 = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        DeviceRebindRequest r3 = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        // r2 裁决为 APPROVED，从 PENDING 集合中退出
        requestRepository.decide(r2.getId(), RebindRequestStatus.APPROVED, userId, Instant.now());

        assertThat(requestRepository.findAll()).extracting(DeviceRebindRequest::getId)
                .containsExactly(r3.getId(), r2.getId(), r1.getId());
        assertThat(requestRepository.findByStatus(RebindRequestStatus.PENDING))
                .extracting(DeviceRebindRequest::getId)
                .containsExactly(r3.getId(), r1.getId());
        assertThat(requestRepository.findByStatus(RebindRequestStatus.APPROVED))
                .extracting(DeviceRebindRequest::getId)
                .containsExactly(r2.getId());
    }

    @Test
    @DisplayName("findPendingByUserId 只返回该用户仍待处理的申请")
    void findPendingByUserIdOnlyReturnsPending() {
        DeviceRebindRequest r1 = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        DeviceRebindRequest r2 = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        requestRepository.decide(r2.getId(), RebindRequestStatus.REJECTED, userId, Instant.now());

        List<DeviceRebindRequest> pendingList = requestRepository.findPendingByUserId(userId);
        assertThat(pendingList).extracting(DeviceRebindRequest::getId).containsExactly(r1.getId());
    }

    @Test
    @DisplayName("supersedePending 只翻动该订阅的 PENDING 行，返回受影响行数；已作废后再调返回 0")
    void supersedePendingOnlyAffectsGivenSubscription() {
        Long otherSubscriptionId = fixtures.createSubscription(userId, AgentType.CODEX, "Codex 席位",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred-2");
        DeviceRebindRequest mine = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        DeviceRebindRequest other = pending(otherSubscriptionId, userId, fromDeviceId, toDeviceId);

        assertThat(requestRepository.supersedePending(subscriptionId)).isEqualTo(1);
        assertThat(requestRepository.findById(mine.getId()).orElseThrow().getStatus())
                .isEqualTo(RebindRequestStatus.SUPERSEDED);
        assertThat(requestRepository.findById(other.getId()).orElseThrow().getStatus())
                .isEqualTo(RebindRequestStatus.PENDING);

        // 已经没有 PENDING 行了，再调不应误伤已作废的行、返回 0
        assertThat(requestRepository.supersedePending(subscriptionId)).isZero();
    }

    @Test
    @DisplayName("decide 是条件更新：PENDING 时生效返回 true，已裁决过的再调返回 false 且不覆盖原裁决")
    void decideIsCompareAndSet() {
        DeviceRebindRequest r = pending(subscriptionId, userId, fromDeviceId, toDeviceId);
        Instant firstDecision = Instant.parse("2026-09-05T00:00:00Z");

        assertThat(requestRepository.decide(r.getId(), RebindRequestStatus.APPROVED, userId, firstDecision)).isTrue();
        DeviceRebindRequest decided = requestRepository.findById(r.getId()).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(RebindRequestStatus.APPROVED);
        assertThat(decided.getDecidedBy()).isEqualTo(userId);
        assertThat(decided.getDecidedAt()).isEqualTo(firstDecision);

        Long otherAdminId = fixtures.createUser("logto-other-admin", null, null);
        Instant secondAttempt = Instant.parse("2026-09-06T00:00:00Z");
        assertThat(requestRepository.decide(r.getId(), RebindRequestStatus.REJECTED, otherAdminId, secondAttempt))
                .isFalse();

        DeviceRebindRequest unchanged = requestRepository.findById(r.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(RebindRequestStatus.APPROVED);
        assertThat(unchanged.getDecidedBy()).isEqualTo(userId);
        assertThat(unchanged.getDecidedAt()).isEqualTo(firstDecision);
    }
}
