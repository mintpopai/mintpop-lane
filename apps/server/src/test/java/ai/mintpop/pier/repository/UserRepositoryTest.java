package ai.mintpop.pier.repository;

import ai.mintpop.pier.dto.UserDto;
import ai.mintpop.pier.enumeration.UserRole;
import ai.mintpop.pier.enumeration.UserStatus;
import ai.mintpop.pier.support.DatabaseFixtures;
import ai.mintpop.pier.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryTest extends MysqlTestBase {

    @Autowired
    private UserRepository repository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private DatabaseFixtures fixtures;
    private Long frontId;
    private Long landId;

    @BeforeEach
    void 准备() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, repository);
        fixtures.清空();
        frontId = fixtures.建FRONT节点("FRONT-1");
        landId = fixtures.建LAND节点("LAND-1", "203.0.113.10");
    }

    @Test
    @DisplayName("用户存取往返，凭据能原样取回")
    void 用户存取往返() {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        UserDto loaded = repository.findBySubject("logto-user-1").orElseThrow();

        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getName()).isEqualTo("测试logto-user-1");
        assertThat(loaded.getRole()).isEqualTo(UserRole.MEMBER);
        assertThat(loaded.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(loaded.getFrontNodeId()).isEqualTo(frontId);
        assertThat(loaded.getLandNodeId()).isEqualTo(landId);
        assertThat(loaded.getClaudeCredential()).isEqualTo("sk-ant-logto-user-1");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("席位凭据在库里是密文")
    void 席位凭据在库里是密文() {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        String stored = fixtures.读原始密文列("app_user", "claude_credential_cipher", id);

        assertThat(stored).isNotBlank().doesNotContain("sk-ant-logto-user-1");
    }

    @Test
    @DisplayName("同一个 Logto 账号不能录两次")
    void 同一个账号不能录两次() {
        fixtures.建用户("logto-user-1", frontId, null);

        assertThatThrownBy(() -> fixtures.建用户("logto-user-1", frontId, null))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(repository.existsBySubject("logto-user-1")).isTrue();
    }

    @Test
    @DisplayName("同一个落地节点绑给第二个人时被数据库拒绝")
    void 同一落地节点不能绑两个人() {
        fixtures.建用户("logto-user-1", frontId, landId);

        assertThatThrownBy(() -> fixtures.建用户("logto-user-2", frontId, landId))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("更新能把落地分配置空——null 必须真的写进库，不能被静默忽略")
    void 更新能把落地分配置空() {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);
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
    void 按落地节点反查占用者() {
        fixtures.建用户("logto-user-1", frontId, landId);

        assertThat(repository.findByLandNodeId(landId))
                .get()
                .extracting(UserDto::getSubject)
                .isEqualTo("logto-user-1");
        assertThat(repository.existsByFrontNodeId(frontId)).isTrue();
        assertThat(repository.existsByFrontNodeId(landId)).isFalse();
    }

    @Test
    @DisplayName("分页搜索：关键字命中姓名或 subject，为空时返回全部")
    void 分页搜索() {
        fixtures.建用户("logto-user-1", frontId, landId);
        fixtures.建用户("logto-user-2", frontId, null);
        fixtures.建用户("另一个人", frontId, null);

        assertThat(repository.search(null, 1, 10).total()).isEqualTo(3);
        assertThat(repository.search("logto-user", 1, 10).total()).isEqualTo(2);
        assertThat(repository.search("另一个", 1, 10).records())
                .singleElement()
                .extracting(UserDto::getSubject)
                .isEqualTo("另一个人");

        var firstPage = repository.search(null, 1, 2);
        assertThat(firstPage.records()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.pageNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("删除用户后落地出口即释放")
    void 删除用户后落地释放() {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.findByLandNodeId(landId)).isEmpty();
    }
}
