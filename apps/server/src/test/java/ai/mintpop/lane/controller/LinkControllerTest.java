package ai.mintpop.lane.controller;

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

import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.REVOKED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LinkControllerTest extends MysqlTestBase {

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

    private Long user1Id;
    private Long user2Id;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long front = fixtures.createFrontNode("FRONT-1");
        Long land1 = fixtures.createLandNode("LAND-1", "77.47.143.6");
        Long land2 = fixtures.createLandNode("LAND-2", "8.8.8.8");
        user1Id = fixtures.createActiveUser("logto-user-1", front, land1, "sk-ant-test-1");
        user2Id = fixtures.createUser("logto-user-2", MEMBER, REVOKED, front, land2);
    }

    @Test
    @DisplayName("无令牌访问接口被拒")
    void requestWithoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/link/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常用户拿到链路配置，业务码为 0")
    void activeUserGetsLinkConfig() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.front.type").value("trojan"))
                .andExpect(jsonPath("$.data.land.server").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.expectedEgressIp").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value("sk-ant-test-1"))
                .andExpect(jsonPath("$.data.agentCredentials[0].agentType").value("CLAUDE"));
    }

    @Test
    @DisplayName("已吊销用户拿不到链路，HTTP 仍为 200 但业务码非 0")
    void revokedUserCannotGetLink() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user2Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(310003))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("心跳返回链路状态")
    void heartbeatReturnsLinkStatus() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("已吊销用户的心跳返回 REVOKED")
    void revokedUserHeartbeatReturnsRevoked() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user2Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }
}
