package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.UserSaveRequest;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 管理员账号保护：管理员不允许被停用、吊销或删除。管理员是进入管理端的唯一钥匙，
 * 处置掉最后一个管理员等于把自己锁在门外，且删除会级联清掉其订阅与资源分配。
 * 资源分配（换第一跳/落地节点）不属于处置，对管理员照常放行。
 */
class AdminUserProtectionTest extends MysqlTestBase {

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
    }

    private UserSaveRequest saveRequest(UserStatus status, Long frontNodeId, Long landNodeId) {
        UserSaveRequest request = new UserSaveRequest();
        request.setStatus(status);
        request.setFrontNodeId(frontNodeId);
        request.setLandNodeId(landNodeId);
        return request;
    }

    @Test
    @DisplayName("管理员不允许被停用")
    void shouldRejectSuspendingAdmin() {
        Long adminId = fixtures.createUser("admin-1", UserRole.ADMIN, UserStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> adminUserService.update(adminId, saveRequest(UserStatus.SUSPENDED, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getBizCode()).isEqualTo(BizCodeEnum.ADMIN_USER_PROTECTED));
        assertThat(userRepository.findById(adminId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("管理员不允许被吊销")
    void shouldRejectRevokingAdmin() {
        Long adminId = fixtures.createUser("admin-1", UserRole.ADMIN, UserStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> adminUserService.update(adminId, saveRequest(UserStatus.REVOKED, null, null)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getBizCode()).isEqualTo(BizCodeEnum.ADMIN_USER_PROTECTED));
        assertThat(userRepository.findById(adminId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("管理员不允许被删除")
    void shouldRejectDeletingAdmin() {
        Long adminId = fixtures.createUser("admin-1", UserRole.ADMIN, UserStatus.ACTIVE, null, null);

        assertThatThrownBy(() -> adminUserService.delete(adminId))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getBizCode()).isEqualTo(BizCodeEnum.ADMIN_USER_PROTECTED));
        assertThat(userRepository.findById(adminId)).isPresent();
    }

    @Test
    @DisplayName("管理员保持 ACTIVE 的资源分配照常放行")
    void shouldAllowNodeAssignmentForActiveAdmin() {
        Long front = fixtures.createFrontNode("front-1");
        Long land = fixtures.createLandNode("land-1", "203.0.113.1");
        Long adminId = fixtures.createUser("admin-1", UserRole.ADMIN, UserStatus.ACTIVE, null, null);

        adminUserService.update(adminId, saveRequest(UserStatus.ACTIVE, front, land));

        assertThat(userRepository.findById(adminId).orElseThrow().getLandNodeId()).isEqualTo(land);
    }

    @Test
    @DisplayName("普通成员的停用与删除不受影响")
    void shouldStillAllowDisposingMember() {
        Long memberId = fixtures.createUser("member-1", UserRole.MEMBER, UserStatus.ACTIVE, null, null);

        adminUserService.update(memberId, saveRequest(UserStatus.SUSPENDED, null, null));
        assertThat(userRepository.findById(memberId).orElseThrow().getStatus()).isEqualTo(UserStatus.SUSPENDED);

        adminUserService.delete(memberId);
        assertThat(userRepository.findById(memberId)).isEmpty();
    }
}
