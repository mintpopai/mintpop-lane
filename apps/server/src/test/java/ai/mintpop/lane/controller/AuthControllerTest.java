package ai.mintpop.lane.controller;

import ai.mintpop.lane.config.AuthProperties;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.security.TicketStore;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends MysqlTestBase {

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
    private TicketStore ticketStore;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private AuthProperties authProperties;

    private DatabaseFixtures fixtures;
    private Long userId;

    private static String s256(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("logto-u1", null, null);
    }

    @Test
    @DisplayName("正确 verifier 兑换出可用的会话 token")
    void exchangeSucceedsAndTokenUsable() throws Exception {
        String verifier = "desktop-verifier-0123456789-0123456789-012345";
        String ticket = ticketStore.create(s256(verifier), userId);

        String body = mockMvc.perform(post("/api/auth/desktop/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"%s\",\"verifier\":\"%s\"}".formatted(ticket, verifier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                // 用 int 字面量：JsonPath 反序列化出 Integer，传 Long 会因类型不等而误报
                .andExpect(jsonPath("$.data.expiresInSeconds").value(2592000))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 兑出的 token 真能过鉴权链
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId));
    }

    @Test
    @DisplayName("错误 verifier 兑换得 TICKET_INVALID")
    void wrongVerifierGetsBizError() throws Exception {
        String ticket = ticketStore.create(s256("real-verifier-0123456789-0123456789-0123"), userId);
        mockMvc.perform(post("/api/auth/desktop/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"%s\",\"verifier\":\"wrong-verifier-0123456789-0123456\"}"
                                .formatted(ticket)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210004));
    }

    @Test
    @DisplayName("同一张票第二次兑换失败")
    void secondExchangeWithSameTicketFails() throws Exception {
        String verifier = "desktop-verifier-0123456789-0123456789-012345";
        String ticket = ticketStore.create(s256(verifier), userId);
        String payload = "{\"ticket\":\"%s\",\"verifier\":\"%s\"}".formatted(ticket, verifier);

        mockMvc.perform(post("/api/auth/desktop/exchange")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/auth/desktop/exchange")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(jsonPath("$.code").value(210004));
    }

    @Test
    @DisplayName("/api/me 返回订阅概览且带在期标记")
    void meReturnsSubscriptionOverview() throws Exception {
        fixtures.createSubscription(userId, AgentType.CLAUDE, "Claude 席位 1",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "cred");
        fixtures.createSubscription(userId, AgentType.CODEX, "Codex 过期席位",
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), null);

        mockMvc.perform(get("/api/me").header("Authorization",
                        "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("logto-u1@test.example"))
                .andExpect(jsonPath("$.data.subscriptions.length()").value(2))
                .andExpect(jsonPath("$.data.subscriptions[0].active").value(true))
                .andExpect(jsonPath("$.data.subscriptions[1].active").value(false))
                // 凭据一个字符都不许出现在响应里
                .andExpect(jsonPath("$.data.subscriptions[0].credential").doesNotExist())
                // 时间字段必须是带 Z 的 UTC 绝对时刻串，这是与前端的契约
                .andExpect(jsonPath("$.data.subscriptions[0].endsAt", matchesPattern(".+Z$")));
    }

    @Test
    @DisplayName("桌面登录入口校验参数并 302 进 OIDC 握手")
    void desktopLoginEntryRedirectsToOidcHandshake() throws Exception {
        mockMvc.perform(get("/auth/desktop/start")
                        .param("code_challenge", "A".repeat(43))
                        .param("state", "desktop-state-01"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String location = result.getResponse().getRedirectedUrl();
                    org.assertj.core.api.Assertions.assertThat(location)
                            .isEqualTo("/oauth2/authorization/logto");
                });

        mockMvc.perform(get("/auth/desktop/start")
                        .param("code_challenge", "非法挑战串")
                        .param("state", "desktop-state-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("桌面流授权请求带 prompt=login 强制重新认证，网页流不带保留 SSO")
    void desktopFlowForcesReauthWhileWebFlowKeepsSso() throws Exception {
        // 桌面端登出只清本地钥匙串，浏览器里 Logto 的 IdP 会话仍在；若不强制重新认证，
        // 重新登录会被 Logto 静默放行，用户根本见不到用户名密码页，「退出」就成了错觉
        jakarta.servlet.http.Cookie flowCookie = mockMvc.perform(get("/auth/desktop/start")
                        .param("code_challenge", "A".repeat(43))
                        .param("state", "desktop-state-01"))
                .andReturn().getResponse().getCookie(AuthProperties.DESKTOP_FLOW_COOKIE_NAME);
        assertThat(flowCookie).isNotNull();

        mockMvc.perform(get("/oauth2/authorization/logto").cookie(flowCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .contains("prompt=login"));

        // 网页流（无桌面中间态 Cookie）不加 prompt，管理端登录保留 IdP 的 SSO 便利
        mockMvc.perform(get("/oauth2/authorization/logto"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .doesNotContain("prompt=login"));
    }

    @Test
    @DisplayName("登出（无 end_session_endpoint 时回退）：302 回管理端，会话 Cookie 被置空过期")
    void logoutClearsSessionCookieAndRedirectsToAdmin() throws Exception {
        // 测试环境的 provider 是显式端点配置，拿不到发现文档 metadata，走回退路径。
        // 断言对照注入的配置值而非写死：本地存在 config/application.yml 时外部配置会盖过测试配置
        mockMvc.perform(get("/auth/logout"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(authProperties.getAdminFrontendUrl()))
                .andExpect(cookie().value(AuthProperties.SESSION_COOKIE_NAME, ""))
                .andExpect(cookie().maxAge(AuthProperties.SESSION_COOKIE_NAME, 0));
    }

    @Test
    @DisplayName("登出（发现文档带 end_session_endpoint）：跳 Logto 结束会话并带 client_id 与回跳地址")
    void logoutRedirectsToLogtoEndSession() throws Exception {
        // 发现模式下 Spring 会把 end_session_endpoint 放进 configurationMetadata，
        // 这里手工构造一个这样的 registration，模拟生产环境（issuer-uri 发现）的形态
        ClientRegistration withDiscoveryMetadata = ClientRegistration.withRegistrationId("logto")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/auth/callback")
                .scope("openid")
                .authorizationUri("http://127.0.0.1:9/oidc/auth")
                .tokenUri("http://127.0.0.1:9/oidc/token")
                .jwkSetUri("http://127.0.0.1:9/oidc/jwks")
                .userNameAttributeName("sub")
                .providerConfigurationMetadata(
                        Map.of("end_session_endpoint", "https://tenant.logto.app/oidc/session/end"))
                .build();
        AuthController controller = new AuthController(ticketStore, sessionTokenService, authProperties,
                userRepository, subscriptionRepository, registrationId -> withDiscoveryMetadata, Clock.systemUTC());

        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.logout(new MockHttpServletRequest(), response);

        assertThat(response.getRedirectedUrl())
                .startsWith("https://tenant.logto.app/oidc/session/end?")
                .contains("client_id=test-client-id")
                // 回跳地址必须整段 URL 编码，否则其中的 :// 会破坏查询串
                .contains("post_logout_redirect_uri="
                        + URLEncoder.encode(authProperties.getAdminFrontendUrl(), StandardCharsets.UTF_8));
        assertThat(response.getCookie(AuthProperties.SESSION_COOKIE_NAME).getMaxAge()).isZero();
    }
}
