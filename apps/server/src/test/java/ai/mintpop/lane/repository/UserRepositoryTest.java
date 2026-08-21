package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends MysqlTestBase {

    @Autowired
    private UserRepository repository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private DatabaseFixtures fixtures;
    private Long frontId;
    private Long landId;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, repository, subscriptionRepository);
        fixtures.clearAll();
        frontId = fixtures.createFrontNode("FRONT-1");
        landId = fixtures.createLandNode("LAND-1", "203.0.113.10");
    }

    @Test
    @DisplayName("用户存取往返，邮箱能原样取回")
    void userRoundTrip() {
        Long id = fixtures.createUser("logto-user-1", frontId, landId);

        UserDto loaded = repository.findBySubject("logto-user-1").orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getName()).isEqualTo("测试logto-user-1");
        assertThat(loaded.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getFrontNodeId()).isEqualTo(frontId);
        assertThat(loaded.getLandNodeId()).isEqualTo(landId);
        assertThat(loaded.getEmail()).isEqualTo("logto-user-1@test.example");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("同一个 Logto 账号不能录两次")
    void sameSubjectCannotBeRegisteredTwice() {
        fixtures.createUser("logto-user-1", frontId, null);

        assertThatThrownBy(() -> fixtures.createUser("logto-user-1", frontId, null))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(repository.findBySubject("logto-user-1")).isPresent();
    }

    @Test
    @DisplayName("同一个落地节点绑给第二个人时被数据库拒绝")
    void sameLandNodeCannotBindTwoUsers() {
        fixtures.createUser("logto-user-1", frontId, landId);

        assertThatThrownBy(() -> fixtures.createUser("logto-user-2", frontId, landId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("更新能把落地分配置空——null 必须真的写进库，不能被静默忽略")
    void updateCanClearLandAssignment() {
        Long id = fixtures.createUser("logto-user-1", frontId, landId);
        UserDto user = repository.findById(id).orElseThrow();
        user.setLandNodeId(null);
        user.setStatus(UserStatus.SUSPENDED);

        repository.update(user);
        UserDto reloaded = repository.findById(id).orElseThrow();

        assertThat(reloaded.getLandNodeId()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        // 落地释放后可以分配给别人
        assertThat(repository.findByLandNodeId(landId)).isEmpty();
    }

    @Test
    @DisplayName("按落地节点反查占用者，供节点删除与分配校验使用")
    void findOccupantByLandNode() {
        fixtures.createUser("logto-user-1", frontId, landId);

        assertThat(repository.findByLandNodeId(landId))
                .get()
                .extracting(UserDto::getSubject)
                .isEqualTo("logto-user-1");
        assertThat(repository.existsByFrontNodeId(frontId)).isTrue();
        assertThat(repository.existsByFrontNodeId(landId)).isFalse();
    }

    @Test
    @DisplayName("分页搜索：关键字命中姓名或 subject，为空时返回全部")
    void pagedSearch() {
        fixtures.createUser("logto-user-1", frontId, landId);
        fixtures.createUser("logto-user-2", frontId, null);
        fixtures.createUser("另一个人", frontId, null);

        assertThat(repository.search(null, null, 1, 10).total()).isEqualTo(3);
        assertThat(repository.search("logto-user", null, 1, 10).total()).isEqualTo(2);
        assertThat(repository.search("另一个", null, 1, 10).records())
                .singleElement()
                .extracting(UserDto::getSubject)
                .isEqualTo("另一个人");

        var firstPage = repository.search(null, null, 1, 2);
        assertThat(firstPage.records()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.pageNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("分页搜索：关键字也能命中邮箱")
    void pagedSearchMatchesEmail() {
        fixtures.createUser("logto-user-1", frontId, landId);
        fixtures.createUser("logto-user-2", frontId, null);

        assertThat(repository.search("logto-user-1@test.example", null, 1, 10).records())
                .singleElement()
                .extracting(UserDto::getSubject)
                .isEqualTo("logto-user-1");
    }

    @Test
    @DisplayName("按有无在期订阅筛选")
    void filterByActiveSubscription() {
        Long withSub = fixtures.createUser("logto-user-1", frontId, landId);
        fixtures.createSubscription(withSub, AgentType.CLAUDE, "Claude 席位",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), null);
        fixtures.createUser("logto-user-2", frontId, null);

        var activeOnly = repository.search(null, true, 1, 10);
        assertThat(activeOnly.total()).isEqualTo(1);
        assertThat(activeOnly.records()).singleElement()
                .extracting(UserDto::getSubject).isEqualTo("logto-user-1");

        var inactiveOnly = repository.search(null, false, 1, 10);
        assertThat(inactiveOnly.total()).isEqualTo(1);
        assertThat(inactiveOnly.records()).singleElement()
                .extracting(UserDto::getSubject).isEqualTo("logto-user-2");
    }

    @Test
    @DisplayName("删除用户后落地出口即释放")
    void deleteUserReleasesLandNode() {
        Long id = fixtures.createUser("logto-user-1", frontId, landId);

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.findByLandNodeId(landId)).isEmpty();
    }
}
