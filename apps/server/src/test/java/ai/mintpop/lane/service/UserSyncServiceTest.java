package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSyncServiceTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserSyncService userSyncService;

    @BeforeEach
    void setUp() {
        new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository).clearAll();
    }

    @Test
    @DisplayName("首次登录自动建档：MEMBER、ACTIVE、无任何资源")
    void firstLoginCreatesProfile() {
        UserDto user = userSyncService.syncOnLogin("logto-new", "new@example.com");

        assertThat(user.getId()).isNotNull();
        UserDto read = userRepository.findBySubject("logto-new").orElseThrow();
        assertThat(read.getEmail()).isEqualTo("new@example.com");
        assertThat(read.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(read.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(read.getFrontNodeId()).isNull();
        assertThat(read.getLandNodeId()).isNull();
    }

    @Test
    @DisplayName("再次登录不重复建档，email 有变化则刷新")
    void repeatLoginRefreshesProfile() {
        Long id = userSyncService.syncOnLogin("logto-a", "old@example.com").getId();
        UserDto again = userSyncService.syncOnLogin("logto-a", "new@example.com");

        assertThat(again.getId()).isEqualTo(id);
        assertThat(userRepository.findById(id).orElseThrow().getEmail()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("刷新资料不动角色/处置态/节点分配")
    void refreshKeepsRoleAndResources() {
        UserDto user = userSyncService.syncOnLogin("logto-b", "b@example.com");
        // 管理员改库提权 + 分配节点
        DatabaseFixtures fixtures =
                new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        Long front = fixtures.createFrontNode("FRONT-1");
        UserDto stored = userRepository.findById(user.getId()).orElseThrow();
        stored.setRole(UserRole.ADMIN);
        stored.setFrontNodeId(front);
        userRepository.update(stored);

        userSyncService.syncOnLogin("logto-b", "b2@example.com");

        UserDto read = userRepository.findById(user.getId()).orElseThrow();
        assertThat(read.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(read.getFrontNodeId()).isEqualTo(front);
        assertThat(read.getEmail()).isEqualTo("b2@example.com");
    }

    @Test
    @DisplayName("邮箱已被别的 Logto 账号占用时首登报错，不静默建档")
    void firstLoginWithTakenEmailFails() {
        userSyncService.syncOnLogin("logto-owner", "shared@example.com");

        assertThatThrownBy(() -> userSyncService.syncOnLogin("logto-other", "shared@example.com"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EMAIL_ALREADY_BOUND);

        assertThat(userRepository.findBySubject("logto-other")).isEmpty();
    }

    @Test
    @DisplayName("改邮箱撞上别人已占用的邮箱时报错，原邮箱保持不变")
    void refreshToTakenEmailFails() {
        userSyncService.syncOnLogin("logto-x", "x@example.com");
        Long yId = userSyncService.syncOnLogin("logto-y", "y@example.com").getId();

        assertThatThrownBy(() -> userSyncService.syncOnLogin("logto-y", "x@example.com"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EMAIL_ALREADY_BOUND);

        assertThat(userRepository.findById(yId).orElseThrow().getEmail()).isEqualTo("y@example.com");
    }
}
