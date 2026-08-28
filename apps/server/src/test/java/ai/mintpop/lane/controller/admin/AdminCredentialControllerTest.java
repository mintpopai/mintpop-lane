package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.enumeration.AgentType;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 席位凭证签发的管理端接口——这两个接口能签出席位账号的真实凭证，
 * 鉴权边界是本测试唯一要覆盖的东西：非管理员身份一律拒绝。
 */
@AutoConfigureMockMvc
class AdminCredentialControllerTest extends MysqlTestBase {

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

    private DatabaseFixtures fixtures;
    /** 带订阅的普通成员，无管理员权限 */
    private Long memberId;
    private Long subscriptionId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long frontId = fixtures.createFrontNode("FRONT-1");
        Long landId = fixtures.createLandNode("LAND-1", "203.0.113.10");
        memberId = fixtures.createUser("logto-m1", frontId, landId);
        subscriptionId = fixtures.createSubscription(memberId, AgentType.CLAUDE, "Claude 席位 1",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), null);
    }

    @Test
    @DisplayName("未带任何身份时，生成授权链接被拒：签发能拿到席位账号的凭证，必须锁死在管理端")
    void authorizeUrlRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions/" + subscriptionId + "/credential/authorize-url"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("以普通成员身份生成授权链接被拒：非管理员没有权限触达该接口")
    void authorizeUrlRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions/" + subscriptionId + "/credential/authorize-url")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未带任何身份时，兑换凭证被拒")
    void exchangeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions/" + subscriptionId + "/credential/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s\",\"code\":\"c\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("以普通成员身份兑换凭证被拒：非管理员没有权限触达该接口")
    void exchangeRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions/" + subscriptionId + "/credential/exchange")
                        .header("Authorization", bearer(memberId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s\",\"code\":\"c\"}"))
                .andExpect(status().isForbidden());
    }
}
