package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.util.RebindRequestNo;
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
import java.util.List;
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
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private UserFrontSubscriptionRepository userFrontSubscriptionRepository;

    @Autowired
    private DeviceRebindRequestRepository rebindRequestRepository;

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

    /**
     * 可变 Map：部分用例要放 null 值，Map.of 不允许。接口收窄为处置态+节点+备注——
     * 第一跳不在这个整体保存里，分配/取消分配走独立的 /front/allocate、/front 接口
     * （见 {@link #allocateAndClearFront()}）。
     */
    private Map<String, Object> updateRequest(String status, Long land) {
        return updateRequest(status, land, null);
    }

    private Map<String, Object> updateRequest(String status, Long land, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("landNodeId", land);
        body.put("remark", remark);
        return body;
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        frontId = fixtures.createFrontNode("FRONT-1");
        landId = fixtures.createLandNode("LAND-1", "203.0.113.10");
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null);
        memberWithSubId = fixtures.createUser("logto-m1", landId);
        fixtures.createSubscription(memberWithSubId, AgentType.CLAUDE, "Claude 席位 1",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "sk-ant-secret");
        memberNoSubId = fixtures.createUser("logto-m2", null);
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
    @DisplayName("列表页按用户各自返回第一跳订阅列表，批量取回时不会串味")
    void listReturnsFrontSubscriptionsPerUserWithoutCrossContamination() throws Exception {
        // 两个用户的第一跳显式配成互不相同的一对一订阅，用于验证批量查询
        // 按 userId 分组正确，不会把 A 的订阅混进 B 的结果、或反过来
        Long airportId = fixtures.createAirport("串味测试机场");
        Long subA = fixtures.createAirportSubscription(airportId, "sub-a", 300);
        Long subB = fixtures.createAirportSubscription(airportId, "sub-b", 300);
        fixtures.assignFront(memberWithSubId, subA);
        fixtures.assignFront(memberNoSubId, subB);

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')].frontSubscriptions[0].subscriptionName")
                        .value("sub-a"))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')]"
                        + ".frontSubscriptions[?(@.subscriptionName=='sub-b')]").isEmpty())
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m2')].frontSubscriptions[0].subscriptionName")
                        .value("sub-b"))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m2')]"
                        + ".frontSubscriptions[?(@.subscriptionName=='sub-a')]").isEmpty());
    }

    @Test
    @DisplayName("按 id 查单个用户，带在期订阅摘要；不存在报 410006")
    void getSingleUser() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + memberWithSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("logto-m1@test.example"))
                .andExpect(jsonPath("$.data.activeSubscriptions[0].agentType").value("CLAUDE"));

        mockMvc.perform(get("/api/admin/users/99999").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410006));
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
                        .content(json(updateRequest("ACTIVE", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("更新只改处置态与节点，subject/email 不受影响")
    void updateOnlyChangesStatusAndNodes() throws Exception {
        var before = userRepository.findById(memberNoSubId).orElseThrow();

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", null))))
                .andExpect(jsonPath("$.code").value(0));

        var user = userRepository.findById(memberNoSubId).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        // 更新接口不收身份字段：邮箱与 subject 的原值不受影响（改邮箱只能靠登录同步）
        assertThat(user.getEmail()).isEqualTo(before.getEmail());
        assertThat(user.getSubject()).isEqualTo("logto-m2");
    }

    @Test
    @DisplayName("删除已分配第一跳的用户不被外键挡住：user_front_subscription 对 app_user 的外键带 "
            + "ON DELETE CASCADE，关联行由数据库自动清掉，应用层不再重复删")
    void deleteUserCascadesFrontSubscriptionAssignment() throws Exception {
        Long airportId = fixtures.createAirport("级联删除测试机场");
        Long subId = fixtures.createAirportSubscription(airportId, "cascade-sub", 300);
        fixtures.assignFront(memberNoSubId, subId);
        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(memberNoSubId)).isNotEmpty();

        mockMvc.perform(delete("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberNoSubId)).isEmpty();
        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(memberNoSubId)).isEmpty();
    }

    @Test
    @DisplayName("容量未满时同一落地节点可以再分配给别人")
    void landNodeWithRemainingCapacityAcceptsMoreUsers() throws Exception {
        // landId 已被 memberWithSub 绑定，容量默认 10，仍有余量
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", landId))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.countByLandNodeId(landId)).isEqualTo(2);
    }

    @Test
    @DisplayName("容量已满的落地节点再分配报 410016")
    void fullLandNodeRejectsAssignment() throws Exception {
        Long tinyLand = fixtures.createLandNode("LAND-容量1", "203.0.113.20", 1);
        fixtures.createUser("logto-m3", tinyLand);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", tinyLand))))
                .andExpect(jsonPath("$.code").value(410016));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getLandNodeId()).isNull();
    }

    @Test
    @DisplayName("重存自己已绑定的节点不占新名额，容量满也能保存")
    void resavingOwnLandNodeDoesNotConsumeCapacity() throws Exception {
        Long tinyLand = fixtures.createLandNode("LAND-容量1", "203.0.113.20", 1);
        Long occupant = fixtures.createUser("logto-m3", tinyLand);

        mockMvc.perform(put("/api/admin/users/" + occupant).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", tinyLand))))
                .andExpect(jsonPath("$.code").value(0));
    }

    private void disableNode(Long nodeId) {
        ProxyNodeDto node = nodeRepository.findById(nodeId).orElseThrow();
        node.setStatus(NodeStatus.DISABLED);
        nodeRepository.update(node);
    }

    @Test
    @DisplayName("禁用的落地节点不许新分配，报 310004 且库里不变")
    void disabledLandNodeRejectsNewAssignment() throws Exception {
        Long disabledLand = fixtures.createLandNode("LAND-已禁用", "203.0.113.30");
        disableNode(disabledLand);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", disabledLand))))
                .andExpect(jsonPath("$.code").value(310004));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getLandNodeId()).isNull();
    }

    @Test
    @DisplayName("已绑在禁用落地节点上的用户，不换节点时仍能保存其它字段")
    void userOnDisabledLandNodeCanStillBeSaved() throws Exception {
        Long disabledLand = fixtures.createLandNode("LAND-已禁用", "203.0.113.30");
        Long occupant = fixtures.createUser("logto-m4", disabledLand);
        disableNode(disabledLand);

        mockMvc.perform(put("/api/admin/users/" + occupant).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", disabledLand, "节点下线待迁移"))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(occupant).orElseThrow().getRemark()).isEqualTo("节点下线待迁移");
    }

    @Test
    @DisplayName("取消分配即回补名额，满员节点随后可以再分配")
    void unassigningRestoresCapacity() throws Exception {
        Long tinyLand = fixtures.createLandNode("LAND-容量1", "203.0.113.20", 1);
        Long occupant = fixtures.createUser("logto-m3", tinyLand);

        mockMvc.perform(put("/api/admin/users/" + occupant).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", tinyLand))))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("节点不存在报 410001，节点角色用错报 410005")
    void nodeValidation() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", 99999L))))
                .andExpect(jsonPath("$.code").value(410001));

        // 把第一跳节点当落地用
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId))))
                .andExpect(jsonPath("$.code").value(410005));
    }

    @Test
    @DisplayName("对不存在的用户做更新或删除报 410006")
    void missingUserFails() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null))))
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
    @DisplayName("删除提过换机申请的用户：级联删掉设备与申请，不被外键挡住")
    void deleteUserCascadesDevicesAndRebindRequests() throws Exception {
        Instant now = Instant.now();
        Long deviceRowId = userDeviceRepository
                .upsert(memberWithSubId, "d".repeat(64), "旧电脑", "macos 26", "", now).getId();
        DeviceRebindRequest request = new DeviceRebindRequest();
        request.setRequestNo(RebindRequestNo.generate(now));
        request.setSubscriptionId(subscriptionRepository.findByUserId(memberWithSubId).getFirst().getId());
        request.setUserId(memberWithSubId);
        request.setFromDeviceId(null);
        request.setToDeviceId(deviceRowId);
        request.setStatus(RebindRequestStatus.PENDING);
        Long requestId = rebindRequestRepository.create(request);

        // 换机申请的外键若不是 ON DELETE CASCADE，这里会直接被数据库挡下、返回 110002
        mockMvc.perform(delete("/api/admin/users/" + memberWithSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberWithSubId)).isEmpty();
        assertThat(rebindRequestRepository.findById(requestId)).isEmpty();
        assertThat(userDeviceRepository.findById(deviceRowId)).isEmpty();
    }

    @Test
    @DisplayName("必填项缺失时报参数错误 110001")
    void missingRequiredFieldReportsParamError() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest(null, null, null))))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("备注可写入，列表与详情都带回")
    void updateWritesRemark() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, "老客户，续费谈过"))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").value("老客户，续费谈过"));
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m2')].remark")
                        .value("老客户，续费谈过"));
    }

    @Test
    @DisplayName("备注可清空：传 null 把原值抹掉")
    void updateClearsRemark() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, "先写一句"))))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").value("先写一句"));

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, null))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").doesNotExist());
    }

    @Test
    @DisplayName("备注超 50 字报参数错误 110001")
    void tooLongRemarkReportsParamError() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, "备".repeat(51)))))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("备注正好 50 字仍可保存——上限是 50，不是 49")
    void remarkAtExactlyFiftyCharsIsAccepted() throws Exception {
        String atLimit = "备".repeat(50);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, atLimit))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").value(atLimit));
    }

    @Test
    @DisplayName("自动分配返回按顺位的列表，详情里 frontSubscriptions 与之一致；取消分配后为空")
    void allocateAndClearFront() throws Exception {
        Long airportId = fixtures.createAirport("泰山云");
        Long subId = fixtures.createAirportSubscription(airportId, "ts-01", 300);
        fixtures.createSubscriptionNode(subId, "🇺🇸[US]Santa Clara 01", NodeStatus.ENABLED);

        mockMvc.perform(post("/api/admin/users/" + memberNoSubId + "/front/allocate")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].position").value(0))
                .andExpect(jsonPath("$.data[0].airportName").value("泰山云"));
        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.frontSubscriptions[0].subscriptionName").value("ts-01"));

        mockMvc.perform(delete("/api/admin/users/" + memberNoSubId + "/front")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.frontSubscriptions.length()").value(0));
    }

    @Test
    @DisplayName("手动分配：PUT /front 按传入顺位落库并标记 frontManual；再点自动分配后标记回到 false")
    void assignFrontManuallyThenAuto() throws Exception {
        Long a = fixtures.createAirport("泰山云");
        Long subA = fixtures.createAirportSubscription(a, "ts-01", 300);
        fixtures.createSubscriptionNode(subA, "🇺🇸[US]Santa Clara 01", NodeStatus.ENABLED);
        Long b = fixtures.createAirport("华山云");
        Long subB = fixtures.createAirportSubscription(b, "hs-01", 300);
        fixtures.createSubscriptionNode(subB, "🇺🇸[US]Los Angeles 01", NodeStatus.ENABLED);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId + "/front").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("airportSubscriptionIds", List.of(subB, subA)))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].subscriptionName").value("hs-01"))
                .andExpect(jsonPath("$.data[1].subscriptionName").value("ts-01"));
        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.frontManual").value(true));

        mockMvc.perform(post("/api/admin/users/" + memberNoSubId + "/front/allocate")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.frontManual").value(false));
    }

    @Test
    @DisplayName("整体保存（改处置态、备注）不动第一跳订阅列表——第一跳只由独立的分配/取消分配接口改写")
    void saveLeavesFrontSubscriptionsIntact() throws Exception {
        Long airportId = fixtures.createAirport("泰山云");
        Long subA = fixtures.createAirportSubscription(airportId, "ts-01", 300);
        Long subB = fixtures.createAirportSubscription(airportId, "ts-02", 300);
        fixtures.assignFront(memberNoSubId, subA, subB);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", null, "老客户"))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(memberNoSubId))
                .containsExactly(subA, subB);
    }

    @Test
    @DisplayName("关键词搜索能命中备注")
    void keywordMatchesRemark() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, "试用期"))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users").param("keyword", "试用")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-m2"));
    }
}
