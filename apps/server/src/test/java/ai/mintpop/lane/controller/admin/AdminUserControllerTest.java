package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.UserStatus;
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
import java.util.HashMap;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户由登录自动建档，管理端不再提供新建入口；这里只覆盖搜索、列表摘要、更新、删除。
 */
@AutoConfigureMockMvc
class AdminUserControllerTest extends MysqlTestBase {

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
    private Long frontId;
    private Long landId;
    private Long adminId;
    /** 带在期订阅的普通成员 */
    private Long memberWithSubId;
    /** 没有任何订阅的普通成员 */
    private Long memberNoSubId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 可变 Map：部分用例要放 null 值，Map.of 不允许。接口收窄为处置态+节点 */
    private Map<String, Object> updateRequest(String status, Long front, Long land) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("frontNodeId", front);
        body.put("landNodeId", land);
        return body;
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        frontId = fixtures.createFrontNode("FRONT-1");
        landId = fixtures.createLandNode("LAND-1", "203.0.113.10");
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, frontId, null);
        memberWithSubId = fixtures.createUser("logto-m1", frontId, landId);
        fixtures.createSubscription(memberWithSubId, AgentType.CLAUDE, "Claude 席位 1",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "sk-ant-secret");
        memberNoSubId = fixtures.createUser("logto-m2", frontId, null);
    }

    @Test
    @DisplayName("列表带 email 与在期订阅摘要")
    void listIncludesEmailAndActiveSubscriptionSummary() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')].email")
                        .value("logto-m1@test.example"))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')].activeSubscriptions[0].agentType")
                        .value("CLAUDE"))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m2')].activeSubscriptions[0]")
                        .isEmpty());
    }

    @Test
    @DisplayName("按有无在期订阅筛选")
    void filterByActiveSubscription() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("hasActiveSubscription", "true")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-m1"));

        mockMvc.perform(get("/api/admin/users").param("hasActiveSubscription", "false")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')]").isEmpty());
    }

    @Test
    @DisplayName("新建用户接口已不存在")
    void createUserEndpointNoLongerExists() throws Exception {
        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("更新只改处置态与节点，subject/email 不受影响")
    void updateOnlyChangesStatusAndNodes() throws Exception {
        var before = userRepository.findById(memberNoSubId).orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", frontId, null))))
                .andExpect(jsonPath("$.code").value(0));

        var user = userRepository.findById(memberNoSubId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        // 更新接口不收身份字段：邮箱与 subject 的原值不受影响（改邮箱只能靠登录同步）
        assertThat(user.getEmail()).isEqualTo(before.getEmail());
        assertThat(user.getSubject()).isEqualTo("logto-m2");
    }

    @Test
    @DisplayName("容量未满时同一落地节点可以再分配给别人")
    void landNodeWithRemainingCapacityAcceptsMoreUsers() throws Exception {
        // landId 已被 memberWithSub 绑定，容量默认 10，仍有余量
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, landId))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.countByLandNodeId(landId)).isEqualTo(2);
    }

    @Test
    @DisplayName("容量已满的落地节点再分配报 410016")
    void fullLandNodeRejectsAssignment() throws Exception {
        Long tinyLand = fixtures.createLandNode("LAND-容量1", "203.0.113.20", 1);
        fixtures.createUser("logto-m3", frontId, tinyLand);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, tinyLand))))
                .andExpect(jsonPath("$.code").value(410016));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getLandNodeId()).isNull();
    }

    @Test
    @DisplayName("重存自己已绑定的节点不占新名额，容量满也能保存")
    void resavingOwnLandNodeDoesNotConsumeCapacity() throws Exception {
        Long tinyLand = fixtures.createLandNode("LAND-容量1", "203.0.113.20", 1);
        Long occupant = fixtures.createUser("logto-m3", frontId, tinyLand);

        mockMvc.perform(put("/api/admin/users/" + occupant).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", frontId, tinyLand))))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("取消分配即回补名额，满员节点随后可以再分配")
    void unassigningRestoresCapacity() throws Exception {
        Long tinyLand = fixtures.createLandNode("LAND-容量1", "203.0.113.20", 1);
        Long occupant = fixtures.createUser("logto-m3", frontId, tinyLand);

        mockMvc.perform(put("/api/admin/users/" + occupant).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, tinyLand))))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("节点不存在报 410001，节点角色用错报 410005")
    void nodeValidation() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", 99999L, null))))
                .andExpect(jsonPath("$.code").value(410001));

        // 把落地节点当第一跳用
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", landId, null))))
                .andExpect(jsonPath("$.code").value(410005));

        // 把第一跳节点当落地用
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, frontId))))
                .andExpect(jsonPath("$.code").value(410005));
    }

    @Test
    @DisplayName("对不存在的用户做更新或删除报 410006")
    void missingUserFails() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null))))
                .andExpect(jsonPath("$.code").value(410006));

        mockMvc.perform(delete("/api/admin/users/99999").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410006));
    }

    @Test
    @DisplayName("删除用户级联删订阅")
    void deleteUserCascadesSubscriptions() throws Exception {
        mockMvc.perform(delete("/api/admin/users/" + memberWithSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberWithSubId)).isEmpty();
        assertThat(subscriptionRepository.findByUserId(memberWithSubId)).isEmpty();
    }

    @Test
    @DisplayName("必填项缺失时报参数错误 110001")
    void missingRequiredFieldReportsParamError() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest(null, frontId, null))))
                .andExpect(jsonPath("$.code").value(110001));
    }
}
