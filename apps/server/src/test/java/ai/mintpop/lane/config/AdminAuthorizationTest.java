package ai.mintpop.lane.config;

import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权链端到端：Bearer 会话 token → SessionAuthFilter 验签 → 查库装角色 → 授权判定。
 * 角色来自 app_user.role 而非 token 本身——token 里只有 userid，提权只能改库。
 */
@AutoConfigureMockMvc
class AdminAuthorizationTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    private Long adminId;
    private Long memberId;

    @BeforeEach
    void 准备数据() {
        DatabaseFixtures fixtures =
                new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.清空();
        Long front = fixtures.建FRONT节点("FRONT-1");
        adminId = fixtures.建用户("logto-admin", ADMIN, ACTIVE, front, null);
        memberId = fixtures.建用户("logto-member", MEMBER, ACTIVE, front, null);
    }

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("无 token 访问业务接口得 401")
    void 无token得401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("MEMBER 的 token 访问管理端得 403")
    void 普通成员访问管理端得403() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 的 token 访问管理端得 200")
    void 管理员访问管理端得200() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminId)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("token 的 userid 在库里已不存在时视同未登录")
    void 已删用户的token视同未登录() throws Exception {
        userRepository.deleteById(memberId);
        mockMvc.perform(get("/api/link/config").header("Authorization", bearer(memberId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("会话 Cookie 也是合法载体（管理端网页用）")
    void 会话Cookie也是合法载体() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .cookie(new jakarta.servlet.http.Cookie(
                                AuthProperties.SESSION_COOKIE_NAME,
                                sessionTokenService.issue(adminId, Duration.ofMinutes(10)))))
                .andExpect(status().isOk());
    }
}
