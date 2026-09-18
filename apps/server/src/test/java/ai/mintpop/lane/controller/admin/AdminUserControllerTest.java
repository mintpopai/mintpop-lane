package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
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

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private UserFrontNodeRepository userFrontNodeRepository;

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

    /** 可变 Map：部分用例要放 null 值，Map.of 不允许。接口收窄为处置态+节点+备注 */
    private Map<String, Object> updateRequest(String status, Long front, Long land) {
        return updateRequest(status, front, land, null);
    }

    private Map<String, Object> updateRequest(String status, Long front, Long land, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        body.put("frontNodeId", front);
        body.put("landNodeId", land);
        body.put("remark", remark);
        return body;
    }

    /** 显式要求按故障域重新分配前置组的那一路；frontNodeId 照旧原样带回，用于验证它被忽略 */
    private Map<String, Object> reallocateRequest(String status, Long front, Long land) {
        Map<String, Object> body = updateRequest(status, front, land, null);
        body.put("reallocateFront", true);
        return body;
    }

    /** 造一个真会被分配器选中的候选前置节点：美国落地（名字带 [US]）+ 已解析的故障域 */
    private Long createAllocatableFrontNode(String name, String failureDomain) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName(name);
        node.setRole(NodeRole.FRONT);
        node.setProtocol(NodeProtocol.TROJAN);
        node.setServerAddr(name + ".example.com");
        node.setPort(443);
        node.setSecret(Map.of("password", "自动分配密码"));
        node.setFailureDomain(failureDomain);
        return nodeRepository.create(node);
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
    @DisplayName("列表页按用户各自返回前置节点组，批量取回时不会串味")
    void listReturnsFrontNodesPerUserWithoutCrossContamination() throws Exception {
        // 两个用户的前置组显式配成互不相同的一对一节点，用于验证批量查询（Map<userId, List<nodeId>>）
        // 按 userId 分组正确，不会把 A 的节点混进 B 的结果、或反过来
        Long secondFront = fixtures.createFrontNode("FRONT-2");
        userFrontNodeRepository.replaceForUser(memberWithSubId, List.of(frontId));
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(secondFront));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')].frontNodes[0].name")
                        .value("FRONT-1"))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m1')].frontNodes[?(@.name=='FRONT-2')]")
                        .isEmpty())
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m2')].frontNodes[0].name")
                        .value("FRONT-2"))
                .andExpect(jsonPath("$.data.records[?(@.subject=='logto-m2')].frontNodes[?(@.name=='FRONT-1')]")
                        .isEmpty());
    }

    @Test
    @DisplayName("按 id 查单个用户，带在期订阅摘要；不存在报 410006")
    void getSingleUser() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + memberWithSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.email").value("logto-m1@test.example"))
                .andExpect(jsonPath("$.data.frontNodeName").value("FRONT-1"))
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
    @DisplayName("只改备注的保存不动前置组——整体保存接口会把 frontNodeId 现值原样带回，"
            + "把它当成「管理员显式指定了单节点」会让二期的冗余在生产里静默塌回一个节点")
    void remarkOnlySaveLeavesFrontGroupIntact() throws Exception {
        Long second = fixtures.createFrontNode("FRONT-2");
        Long third = fixtures.createFrontNode("FRONT-3");
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(frontId, second, third));

        // 逐字模拟管理端保存备注时的载荷：frontNodeId 是 userToForm 摊平带回来的现值
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null, "老客户"))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId))
                .containsExactly(frontId, second, third);
        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getRemark()).isEqualTo("老客户");
    }

    @Test
    @DisplayName("只改处置态的保存同样不动前置组——停用/恢复/吊销走的也是这个整体保存接口")
    void statusOnlySaveLeavesFrontGroupIntact() throws Exception {
        Long second = fixtures.createFrontNode("FRONT-2");
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(frontId, second));

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("SUSPENDED", frontId, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.SUSPENDED);
        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId))
                .containsExactly(frontId, second);

        // 恢复也是同一条路，一并验一次：两次处置转换下来组仍是原样
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId))
                .containsExactly(frontId, second);
    }

    @Test
    @DisplayName("显式指定另一个前置节点：手工指定仍然允许，user_front_node 收敛成这一个")
    void updateWithExplicitFrontNodeReplacesSetWithJustThatNode() throws Exception {
        Long second = fixtures.createFrontNode("FRONT-2");
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(frontId, second));

        // 指定的节点与库里现值（frontId）不同，才是「管理员这次真的改了第一跳」
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", second, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getFrontNodeId()).isEqualTo(second);
        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId)).containsExactly(second);
    }

    @Test
    @DisplayName("第一跳选「不分配」就真的不分配：front_node_id 置空、关联表清空——"
            + "这是取消分配、腾出节点以便删除的唯一入口")
    void clearingFrontNodeEmptiesPrimaryAndGroup() throws Exception {
        Long second = fixtures.createFrontNode("FRONT-2");
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(frontId, second));
        // 库里存在一个可被分配器选中的候选：二期上线时 frontNodeId=null 是「自动分配」的暗号，
        // 于是「不分配」反而会把这个节点分下去。有它在，本用例才真的守得住「不分配就是不分配」
        createAllocatableFrontNode("🇺🇸[US]Auto-01", "relay.auto.example.net");

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", null, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getFrontNodeId()).isNull();
        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId)).isEmpty();
    }

    @Test
    @DisplayName("显式要求重新分配时才调分配器：按故障域算出的全集写入 user_front_node，"
            + "主节点写入 front_node_id，且 frontNodeId 带回来的现值被忽略")
    void reallocateFrontRebuildsGroupByFailureDomain() throws Exception {
        // fixtures.createFrontNode 造出的节点名字不含美国标记、failureDomain 也是 null，不会被分配器选中；
        // 这里单独造两个真正会被选中的候选：美国落地 + 已解析的故障域
        Long autoFirst = createAllocatableFrontNode("🇺🇸[US]Auto-01", "relay.auto.example.net");
        Long autoSecond = createAllocatableFrontNode("🇺🇸[US]Auto-02", "relay.auto.example.net");
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(frontId));

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(reallocateRequest("ACTIVE", frontId, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberNoSubId).orElseThrow().getFrontNodeId()).isEqualTo(autoFirst);
        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId))
                .containsExactly(autoFirst, autoSecond);
    }

    @Test
    @DisplayName("删除已分配前置节点的用户不被外键挡住：user_front_node 对 app_user 的外键带 "
            + "ON DELETE CASCADE，关联行由数据库自动清掉，应用层不再重复删")
    void deleteUserCascadesFrontNodeAssignment() throws Exception {
        userFrontNodeRepository.replaceForUser(memberNoSubId, List.of(frontId));
        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId)).isNotEmpty();

        mockMvc.perform(delete("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(memberNoSubId)).isEmpty();
        assertThat(userFrontNodeRepository.findNodeIdsByUserId(memberNoSubId)).isEmpty();
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
                        .content(json(updateRequest(null, frontId, null))))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("备注可写入，列表与详情都带回")
    void updateWritesRemark() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null, "老客户，续费谈过"))))
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
                        .content(json(updateRequest("ACTIVE", frontId, null, "先写一句"))))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").value("先写一句"));

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null, null))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").doesNotExist());
    }

    @Test
    @DisplayName("备注超 50 字报参数错误 110001")
    void tooLongRemarkReportsParamError() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null, "备".repeat(51)))))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("备注正好 50 字仍可保存——上限是 50，不是 49")
    void remarkAtExactlyFiftyCharsIsAccepted() throws Exception {
        String atLimit = "备".repeat(50);

        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null, atLimit))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.remark").value(atLimit));
    }

    @Test
    @DisplayName("关键词搜索能命中备注")
    void keywordMatchesRemark() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + memberNoSubId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(updateRequest("ACTIVE", frontId, null, "试用期"))))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users").param("keyword", "试用")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-m2"));
    }
}
