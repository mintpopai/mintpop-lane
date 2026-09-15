package ai.mintpop.lane.controller;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** 待开通订阅（起止为空）在各出口的表现：不在期、不下发凭据、不许签发；管理员填起期后即在期 */
@AutoConfigureMockMvc
class PendingSubscriptionTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private SessionTokenService sessionTokenService;

    private Long adminId;
    private Long memberId;
    private Long pendingId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long frontId = fixtures.createFrontNode("FRONT-1");
        Long landId = fixtures.createLandNode("LAND-1", "203.0.113.10");
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, frontId, landId);
        memberId = fixtures.createUser("logto-member", MEMBER, ACTIVE, frontId, landId);
        // 起止都传 null 就是待开通；凭据留空
        pendingId = fixtures.createSubscription(memberId, AgentType.CLAUDE, "Claude 月付", null, null, null);
    }

    @Test
    @DisplayName("/api/me 列出待开通订阅：起止为 null、active 为 false")
    void meShowsPendingAsInactive() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(memberId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.subscriptions[0].id").value(pendingId))
                .andExpect(jsonPath("$.data.subscriptions[0].startsAt").doesNotExist())
                .andExpect(jsonPath("$.data.subscriptions[0].endsAt").doesNotExist())
                .andExpect(jsonPath("$.data.subscriptions[0].active").value(false));
    }

    @Test
    @DisplayName("链路配置不下发待开通订阅的凭据")
    void linkConfigOmitsPending() throws Exception {
        mockMvc.perform(get("/api/link/config").header("Authorization", bearer(memberId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.agentCredentials").isEmpty());
    }

    @Test
    @DisplayName("管理端列表里待开通订阅起止为 null，用户列表的在期摘要不含它")
    void adminListShowsNullDates() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].startsAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].endsAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].credentialStale").value(false));
        mockMvc.perform(get("/api/admin/users/" + memberId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.activeSubscriptions").isEmpty());
    }

    @Test
    @DisplayName("对待开通订阅发起凭证签发报 410041，先于链路与出口探测")
    void issueRejectedBeforeActivation() throws Exception {
        mockMvc.perform(post("/api/admin/subscriptions/" + pendingId + "/credential/authorize-url")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410041));
    }

    @Test
    @DisplayName("管理员填起期即开通：止期按快照时长算出，随后在期")
    void adminFillsStartToActivate() throws Exception {
        mockMvc.perform(put("/api/admin/subscriptions/" + pendingId)
                        .header("Authorization", bearer(adminId))
                        .contentType("application/json")
                        .content("{\"startsAt\":\"2026-09-01T00:00:00Z\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].startsAt", containsString("2026-09-01T00:00:00")))
                .andExpect(jsonPath("$.data[0].endsAt", containsString("2026-10-01T00:00:00")));
    }
}
