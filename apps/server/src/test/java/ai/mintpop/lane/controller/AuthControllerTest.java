package ai.mintpop.lane.controller;

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
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    private DatabaseFixtures fixtures;
    private Long userId;

    private static String s256(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    @BeforeEach
    void 准备数据() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.清空();
        userId = fixtures.建用户("logto-u1", null, null);
    }

    @Test
    @DisplayName("正确 verifier 兑换出可用的会话 token")
    void 兑换成功且token可用() throws Exception {
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
    void 错误verifier得业务错误() throws Exception {
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
    void 同票二兑失败() throws Exception {
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
    void me返回订阅概览() throws Exception {
        fixtures.建订阅(userId, AgentType.CLAUDE, "Claude 席位 1",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), "cred");
        fixtures.建订阅(userId, AgentType.CODEX, "Codex 过期席位",
                LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(1), null);

        mockMvc.perform(get("/api/me").header("Authorization",
                        "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("logto-u1@test.example"))
                .andExpect(jsonPath("$.data.subscriptions.length()").value(2))
                .andExpect(jsonPath("$.data.subscriptions[0].active").value(true))
                .andExpect(jsonPath("$.data.subscriptions[1].active").value(false))
                // 凭据一个字符都不许出现在响应里
                .andExpect(jsonPath("$.data.subscriptions[0].credential").doesNotExist());
    }

    @Test
    @DisplayName("桌面登录入口校验参数并 302 进 OIDC 握手")
    void 桌面登录入口跳转握手() throws Exception {
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
}
