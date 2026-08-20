package ai.mintpop.lane.security;

import ai.mintpop.lane.config.AuthProperties;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.service.UserSyncService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 直接驱动成功处理器：验证「建档 + 桌面/网页分叉」两条出口。 */
class OidcLoginSuccessHandlerTest extends MysqlTestBase {

    @Autowired
    private OidcLoginSuccessHandler handler;

    @Autowired
    private AuthProperties authProperties;

    @Autowired
    private DesktopFlowCookie desktopFlowCookie;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @BeforeEach
    void 清库() {
        new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository).清空();
    }

    private static OidcUser oidc用户(String subject, String email, String name) {
        OidcIdToken idToken = new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", subject, "email", email, "name", name));
        return new DefaultOidcUser(java.util.List.of(), idToken);
    }

    @Test
    @DisplayName("网页登录：建档 + 会话 Cookie + 302 管理端")
    void 网页登录发会话Cookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response,
                new TestingAuthenticationToken(oidc用户("logto-web", "web@example.com", "网页用户"), null));

        assertThat(userRepository.findBySubject("logto-web")).isPresent();
        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith(AuthProperties.SESSION_COOKIE_NAME + "="));
        // 硬验收项（Task 3 安全评审）：会话 Cookie 必须带 SameSite=Lax 与 HttpOnly
        assertThat(response.getHeaders("Set-Cookie"))
                .filteredOn(c -> c.startsWith(AuthProperties.SESSION_COOKIE_NAME + "="))
                .allSatisfy(c -> assertThat(c).contains("SameSite=Lax").contains("HttpOnly"));
        // 对照注入的配置值而非写死：本地存在 config/application.yml 时外部配置会盖过测试配置
        assertThat(response.getRedirectedUrl()).isEqualTo(authProperties.getAdminFrontendUrl());
    }

    @Test
    @DisplayName("桌面登录：建档 + 深链带 ticket 与 state，不发会话 Cookie")
    void 桌面登录深链带ticket() throws Exception {
        // 先经 DesktopFlowCookie 写入中间态，再把 Cookie 搬进登录回调请求
        MockHttpServletRequest startRequest = new MockHttpServletRequest();
        MockHttpServletResponse startResponse = new MockHttpServletResponse();
        desktopFlowCookie.write(startRequest, startResponse, "A".repeat(43), "desktop-state-01");
        String cookieValue = startResponse.getHeader("Set-Cookie")
                .split(";")[0].split("=", 2)[1];

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(
                AuthProperties.DESKTOP_FLOW_COOKIE_NAME, cookieValue));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response,
                new TestingAuthenticationToken(oidc用户("logto-desk", "d@example.com", "桌面用户"), null));

        assertThat(userRepository.findBySubject("logto-desk")).isPresent();
        assertThat(response.getRedirectedUrl())
                .startsWith("lane://callback?ticket=")
                .contains("state=desktop-state-01");
        assertThat(response.getHeaders("Set-Cookie"))
                .noneMatch(c -> c.startsWith(AuthProperties.SESSION_COOKIE_NAME + "="));
    }
}
