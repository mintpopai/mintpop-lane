package ai.mintpop.lane.config;

import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    /**
     * 只在这一条端到端用例里用到：其余用例走 {@code jwt()} 后处理器直接注入认证对象，
     * 不经过真实解码，因此不需要它。这里用 {@code @MockitoBean} 把生产环境里连
     * Logto JWKS 端点的 {@link JwtDecoder} 换成假的，好让请求真正走一遍
     * {@code oauth2ResourceServer().jwt()} 过滤器 → {@code DbRoleJwtAuthenticationConverter}
     * → 授权判定这条完整链路——如果把 {@code SecurityConfig} 里接线转换器的那行删掉，
     * 这条用例会变红，而其余用 {@code jwt()} 后处理器的用例不会。
     */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static Jwt 假Jwt(String subject, String tokenValue) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .claim("sub", subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("无令牌访问管理端接口得 401")
    void 无令牌访问管理端接口得401() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通成员访问管理端接口得 403")
    void 普通成员访问管理端接口得403() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("没有任何权限的令牌（库里查无此人）访问管理端接口得 403")
    void 无权限令牌访问管理端接口得403() throws Exception {
        mockMvc.perform(get("/api/admin/nodes").with(jwt().authorities()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员能通过授权关卡访问管理端接口")
    void 管理员通过授权关卡() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("端到端：库里的 role 经真实过滤器链转换成权限——ADMIN 放行、MEMBER 拒绝")
    void 库里的role经真实过滤器链授权() throws Exception {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.清空();
        Long front = fixtures.建FRONT节点("FRONT-1");
        fixtures.建用户("admin-sub", UserRole.ADMIN, UserStatus.ACTIVE, front, null);
        fixtures.建用户("member-sub", UserRole.MEMBER, UserStatus.ACTIVE, front, null);

        when(jwtDecoder.decode("admin-token")).thenReturn(假Jwt("admin-sub", "admin-token"));
        when(jwtDecoder.decode("member-token")).thenReturn(假Jwt("member-sub", "member-token"));

        // 不用 jwt() 后处理器：它会绕过 DbRoleJwtAuthenticationConverter 直接注入权限，
        // 那样测的就不是「库里的 role 能不能落地成权限」，而是白测
        mockMvc.perform(get("/api/admin/nodes").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/nodes").header("Authorization", "Bearer member-token"))
                .andExpect(status().isForbidden());
    }
}
