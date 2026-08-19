package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
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
    void 清库() {
        new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository).清空();
    }

    @Test
    @DisplayName("首次登录自动建档：MEMBER、ACTIVE、无任何资源")
    void 首次登录自动建档() {
        UserDto user = userSyncService.syncOnLogin("logto-new", "new@example.com", "小新");

        assertThat(user.getId()).isNotNull();
        UserDto read = userRepository.findBySubject("logto-new").orElseThrow();
        assertThat(read.getEmail()).isEqualTo("new@example.com");
        assertThat(read.getName()).isEqualTo("小新");
        assertThat(read.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(read.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(read.getFrontNodeId()).isNull();
        assertThat(read.getLandNodeId()).isNull();
    }

    @Test
    @DisplayName("再次登录不重复建档，email 与 name 有变化则刷新")
    void 再次登录刷新资料() {
        Long id = userSyncService.syncOnLogin("logto-a", "old@example.com", "旧名").getId();
        UserDto again = userSyncService.syncOnLogin("logto-a", "new@example.com", "新名");

        assertThat(again.getId()).isEqualTo(id);
        UserDto read = userRepository.findById(id).orElseThrow();
        assertThat(read.getEmail()).isEqualTo("new@example.com");
        assertThat(read.getName()).isEqualTo("新名");
    }

    @Test
    @DisplayName("刷新资料不动角色/处置态/节点分配")
    void 刷新不动权限与资源() {
        UserDto user = userSyncService.syncOnLogin("logto-b", "b@example.com", "乙");
        // 管理员改库提权 + 分配节点
        DatabaseFixtures fixtures =
                new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        Long front = fixtures.建FRONT节点("FRONT-1");
        UserDto stored = userRepository.findById(user.getId()).orElseThrow();
        stored.setRole(UserRole.ADMIN);
        stored.setFrontNodeId(front);
        userRepository.update(stored);

        userSyncService.syncOnLogin("logto-b", "b2@example.com", "乙");

        UserDto read = userRepository.findById(user.getId()).orElseThrow();
        assertThat(read.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(read.getFrontNodeId()).isEqualTo(front);
        assertThat(read.getEmail()).isEqualTo("b2@example.com");
    }

    @Test
    @DisplayName("Logto 未提供姓名时用邮箱 @ 前缀兜底")
    void 缺姓名用邮箱前缀兜底() {
        UserDto user = userSyncService.syncOnLogin("logto-c", "carol@example.com", null);
        assertThat(user.getName()).isEqualTo("carol");
    }
}
