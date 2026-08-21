package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminSubscriptionControllerTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private Long adminId;
    private Long memberId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 可变 Map：部分用例要留空某字段，Map.of 不允许放 null */
    private Map<String, Object> request(String agentType, String name, String startsAt, String endsAt,
                                   String credential) {
        Map<String, Object> body = new HashMap<>();
        body.put("agentType", agentType);
        body.put("name", name);
        body.put("startsAt", startsAt);
        body.put("endsAt", endsAt);
        body.put("credential", credential);
        return body;
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long frontId = fixtures.createFrontNode("FRONT-1");
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, frontId, null);
        memberId = fixtures.createUser("logto-member", MEMBER, ACTIVE, frontId, null);
    }

    @Test
    @DisplayName("新建订阅并回读：列表里只看得到 hasCredential，看不到凭据本体")
    void createSubscriptionAndReadBack() throws Exception {
        String body = json(request("CLAUDE", "Claude 席位 1",
                "2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z", "sk-ant-x"));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].agentType").value("CLAUDE"))
                .andExpect(jsonPath("$.data[0].name").value("Claude 席位 1"))
                .andExpect(jsonPath("$.data[0].hasCredential").value(true))
                .andExpect(content().string(not(containsString("sk-ant-x"))));
    }

    @Test
    @DisplayName("给不存在的用户建订阅报 410006")
    void createSubscriptionForMissingUser() throws Exception {
        String body = json(request("CLAUDE", "Claude 席位 1",
                "2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z", "sk-ant-x"));

        mockMvc.perform(post("/api/admin/users/99999/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(410006));
    }

    @Test
    @DisplayName("止期不晚于起期报 110001")
    void endNotAfterStartFails() throws Exception {
        String body = json(request("CLAUDE", "Claude 席位 1",
                "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z", "sk-ant-x"));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("更新时留空凭据沿用原值")
    void updateKeepsCredentialWhenOmitted() throws Exception {
        String create = json(request("CLAUDE", "Claude 席位 1",
                "2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z", "sk-ant-x"));
        String response = mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("data").asLong();

        String update = json(request("CLAUDE", "Claude 席位 1（改名）",
                "2026-08-01T00:00:00Z", "2026-10-01T00:00:00Z", null));
        mockMvc.perform(put("/api/admin/subscriptions/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("Claude 席位 1（改名）"))
                .andExpect(jsonPath("$.data[0].hasCredential").value(true));
    }

    @Test
    @DisplayName("更新不存在的订阅报 410008")
    void updateMissingSubscription() throws Exception {
        String update = json(request("CLAUDE", "Claude 席位 1",
                "2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z", null));

        mockMvc.perform(put("/api/admin/subscriptions/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(jsonPath("$.code").value(410008));
    }

    @Test
    @DisplayName("删除不存在的订阅报 410008")
    void deleteMissingSubscription() throws Exception {
        mockMvc.perform(delete("/api/admin/subscriptions/99999").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410008));
    }

    @Test
    @DisplayName("删除订阅后列表变空")
    void deleteSubscription() throws Exception {
        String create = json(request("CLAUDE", "Claude 席位 1",
                "2026-08-01T00:00:00Z", "2026-09-01T00:00:00Z", "sk-ant-x"));
        String response = mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("data").asLong();

        mockMvc.perform(delete("/api/admin/subscriptions/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("MEMBER 访问订阅接口得 403")
    void memberAccessGets403() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
    }
}
