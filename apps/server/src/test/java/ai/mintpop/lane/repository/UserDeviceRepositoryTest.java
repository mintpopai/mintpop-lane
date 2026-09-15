package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserDeviceRepositoryTest extends MysqlTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private UserDeviceRepository deviceRepository;

    private DatabaseFixtures fixtures;
    private Long userId;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("logto-device-owner", null, null);
    }

    @Test
    @DisplayName("upsert 首次登记：新建一行，first_seen_at 与 last_seen_at 相同")
    void upsertCreatesNewRow() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");

        UserDevice created = deviceRepository.upsert(userId, "device-aaa", "MacBook Pro", "macos 26.6.1", "Mac17,9", now);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getDeviceId()).isEqualTo("device-aaa");
        assertThat(created.getName()).isEqualTo("MacBook Pro");
        assertThat(created.getOs()).isEqualTo("macos 26.6.1");
        assertThat(created.getModel()).isEqualTo("Mac17,9");
        assertThat(created.getFirstSeenAt()).isEqualTo(now);
        assertThat(created.getLastSeenAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("upsert 再次上报：刷新展示信息与 last_seen_at，但 first_seen_at 保持不变")
    void upsertUpdatesExistingRowKeepsFirstSeenAt() {
        Instant firstSeen = Instant.parse("2026-09-01T00:00:00Z");
        Instant lastSeen = Instant.parse("2026-09-10T08:30:00Z");

        UserDevice created = deviceRepository.upsert(userId, "device-aaa", "老主机名", "macos 15.0", "", firstSeen);
        UserDevice updated = deviceRepository.upsert(userId, "device-aaa", "新主机名", "macos 26.6.1", "Mac17,9", lastSeen);

        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getName()).isEqualTo("新主机名");
        assertThat(updated.getOs()).isEqualTo("macos 26.6.1");
        assertThat(updated.getModel()).isEqualTo("Mac17,9");
        assertThat(updated.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(updated.getLastSeenAt()).isEqualTo(lastSeen);

        UserDevice reread = deviceRepository.findById(created.getId()).orElseThrow();
        assertThat(reread.getName()).isEqualTo("新主机名");
        assertThat(reread.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(reread.getLastSeenAt()).isEqualTo(lastSeen);
    }

    @Test
    @DisplayName("同一机器码归属不同用户各自独立成行，互不覆盖")
    void sameDeviceIdDifferentUsersAreSeparateRows() {
        Long otherUserId = fixtures.createUser("logto-other-owner", null, null);
        Instant now = Instant.parse("2026-09-01T00:00:00Z");

        UserDevice mine = deviceRepository.upsert(userId, "device-shared", "我的机器", "macos 26", "", now);
        UserDevice theirs = deviceRepository.upsert(otherUserId, "device-shared", "他的机器", "windows 11", "", now);

        assertThat(mine.getId()).isNotEqualTo(theirs.getId());
        assertThat(deviceRepository.findByUserId(userId)).extracting(UserDevice::getId).containsExactly(mine.getId());
        assertThat(deviceRepository.findByUserId(otherUserId)).extracting(UserDevice::getId).containsExactly(theirs.getId());
    }

    @Test
    @DisplayName("findByUserId 与 findById")
    void findByUserIdAndFindById() {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        UserDevice a = deviceRepository.upsert(userId, "device-a", "机器 A", "macos 26", "", now);
        UserDevice b = deviceRepository.upsert(userId, "device-b", "机器 B", "windows 11", "", now);

        List<UserDevice> devices = deviceRepository.findByUserId(userId);
        assertThat(devices).extracting(UserDevice::getId).containsExactlyInAnyOrder(a.getId(), b.getId());

        assertThat(deviceRepository.findById(a.getId())).isPresent();
        assertThat(deviceRepository.findById(999_999L)).isEmpty();
    }
}
