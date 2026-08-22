package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
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
import java.util.List;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import ai.mintpop.lane.enumeration.NodeStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminNodeControllerTest extends MysqlTestBase {

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

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
    }

    @Test
    @DisplayName("新建节点并在列表里看到它，敏感键只返回「已配置」标记")
    void createNodeAndListIt() throws Exception {
        var body = Map.of(
                "name", "LAND-东京-03",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "extraConfig", Map.of("udp", true),
                "secret", Map.of("username", "u1", "password", "落地密码"),
                "egressIp", "203.0.113.10",
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/admin/nodes").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("LAND-东京-03"))
                .andExpect(jsonPath("$.data[0].protocol").value("SOCKS5"))
                .andExpect(jsonPath("$.data[0].extraConfig.udp").value(true))
                .andExpect(jsonPath("$.data[0].egressIp").value("203.0.113.10"))
                .andExpect(jsonPath("$.data[0].secretConfigured").value(true))
                // 密码一个字符都不许回传
                .andExpect(jsonPath("$.data[0].secret").doesNotExist())
                // 未传容量时走默认值 10；尚未分配给任何人
                .andExpect(jsonPath("$.data[0].capacity").value(10))
                .andExpect(jsonPath("$.data[0].assignedUserCount").value(0));
    }

    @Test
    @DisplayName("新建落地节点带自定义容量，列表回显该容量")
    void createLandNodeWithCustomCapacity() throws Exception {
        var body = Map.of(
                "name", "LAND-东京-05",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.12",
                "port", 50101,
                "secret", Map.of("username", "u1", "password", "落地密码"),
                "egressIp", "203.0.113.12",
                "capacity", 3,
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/nodes").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].capacity").value(3));
    }

    @Test
    @DisplayName("容量小于 1 时报参数错误 110001，节点不落库")
    void capacityBelowOneFails() throws Exception {
        var body = Map.of(
                "name", "LAND-东京-05",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.12",
                "port", 50101,
                "secret", Map.of("username", "u1", "password", "落地密码"),
                "capacity", 0,
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
        assertThat(nodeRepository.findAll(null)).isEmpty();
    }

    @Test
    @DisplayName("更新落地节点可以修改容量")
    void updateLandNodeCapacity() throws Exception {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");
        var body = Map.of(
                "name", "LAND-1",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "egressIp", "203.0.113.10",
                "capacity", 25,
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id).orElseThrow().getCapacity()).isEqualTo(25);
    }

    @Test
    @DisplayName("第一跳节点的容量与已绑人数在列表里为 null（容量是落地专属概念）")
    void frontNodeHasNoCapacityInResponse() throws Exception {
        fixtures.createFrontNode("FRONT-1");

        mockMvc.perform(get("/api/admin/nodes").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].capacity").doesNotExist())
                .andExpect(jsonPath("$.data[0].assignedUserCount").doesNotExist());
    }

    @Test
    @DisplayName("新建落地节点带出口时区，列表回显该时区")
    void createLandNodeWithEgressTimezone() throws Exception {
        var body = Map.of(
                "name", "LAND-东京-03",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "secret", Map.of("username", "u1", "password", "落地密码"),
                "egressIp", "203.0.113.10",
                "egressTimezone", "Asia/Tokyo",
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/nodes").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].egressTimezone").value("Asia/Tokyo"));
    }

    @Test
    @DisplayName("出口时区不是合法 IANA 时区名时报 410015，节点不落库")
    void invalidEgressTimezoneFails() throws Exception {
        var body = Map.of(
                "name", "LAND-东京-03",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "secret", Map.of("username", "u1", "password", "落地密码"),
                "egressIp", "203.0.113.10",
                "egressTimezone", "东京时间",
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410015));
        assertThat(nodeRepository.findAll(null)).isEmpty();
    }

    @Test
    @DisplayName("非落地节点提交的出口时区被忽略，落库为 null")
    void egressTimezoneIgnoredForFrontNode() throws Exception {
        var body = Map.of(
                "name", "FRONT-1",
                "role", "FRONT",
                "protocol", "TROJAN",
                "serverAddr", "us.example.com",
                "port", 443,
                "secret", Map.of("password", "p1"),
                "egressTimezone", "Asia/Tokyo",
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findAll(null).get(0).getEgressTimezone()).isNull();
    }

    @Test
    @DisplayName("按角色过滤节点列表")
    void filterNodesByRole() throws Exception {
        fixtures.createFrontNode("FRONT-1");
        fixtures.createLandNode("LAND-1", "203.0.113.10");

        mockMvc.perform(get("/api/admin/nodes").param("role", "FRONT").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("FRONT"));
    }

    @Test
    @DisplayName("落地节点已分配时列表里显示已绑人数")
    void landNodeShowsAssignedUserCount() throws Exception {
        Long front = fixtures.createFrontNode("FRONT-1");
        Long land = fixtures.createLandNode("LAND-1", "203.0.113.10");
        fixtures.createUser("logto-user-1", front, land);
        fixtures.createUser("logto-user-2", front, land);

        mockMvc.perform(get("/api/admin/nodes").param("role", "LAND").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].assignedUserCount").value(2));
    }

    @Test
    @DisplayName("更新节点时敏感键留空表示不改")
    void updateKeepsSecretWhenOmitted() throws Exception {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");
        var body = Map.of(
                "name", "LAND-1-改名",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "egressIp", "203.0.113.10",
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id).orElseThrow().getSecret())
                .containsEntry("password", "land-密码");
        assertThat(nodeRepository.findById(id).orElseThrow().getName()).isEqualTo("LAND-1-改名");
    }

    @Test
    @DisplayName("更新节点改名撞上其它节点的名字时报 410007")
    void updateRenameConflictFails() throws Exception {
        Long a = fixtures.createFrontNode("FRONT-A");
        Long b = fixtures.createLandNode("LAND-B", "203.0.113.10");
        var body = Map.of(
                "name", "FRONT-A",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "egressIp", "203.0.113.10",
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + b).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410007));

        assertThat(nodeRepository.findById(a).orElseThrow().getName()).isEqualTo("FRONT-A");
        assertThat(nodeRepository.findById(b).orElseThrow().getName()).isEqualTo("LAND-B");
    }

    @Test
    @DisplayName("更新节点时把名字改成它自己原来的名字，不误报重复")
    void updateWithOwnNameNotFlaggedAsDuplicate() throws Exception {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");
        var body = Map.of(
                "name", "LAND-1",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50102,
                "egressIp", "203.0.113.10",
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id).orElseThrow().getPort()).isEqualTo(50102);
    }

    @Test
    @DisplayName("节点名重复时报 410007")
    void duplicateNodeNameFails() throws Exception {
        fixtures.createFrontNode("FRONT-1");
        var body = Map.of("name", "FRONT-1", "role", "FRONT", "protocol", "TROJAN",
                "serverAddr", "us.example.com", "port", 443, "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410007));
    }

    @Test
    @DisplayName("端口非法时报参数错误 110001，而不是 500")
    void invalidPortReportsParamError() throws Exception {
        var body = Map.of("name", "X", "role", "FRONT", "protocol", "TROJAN",
                "serverAddr", "us.example.com", "port", 70000, "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("角色查询参数取值非法（枚举转换失败）时报参数错误 110001，而不是 500")
    void invalidRoleParamReportsParamError() throws Exception {
        mockMvc.perform(get("/api/admin/nodes").param("role", "不存在的角色").header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("请求体里字段类型错（port 传字符串）时报参数错误 110001，而不是内部错误 110002")
    void wrongFieldTypeReportsParamError() throws Exception {
        String body = """
                {"name":"X","role":"FRONT","protocol":"TROJAN",
                 "serverAddr":"us.example.com","port":"abc","status":"ENABLED"}""";

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("敏感键混进 extraConfig 时报参数错误 110001，且不会明文落库")
    void secretKeyInExtraConfigFails() throws Exception {
        var body = Map.of(
                "name", "FRONT-混入密码",
                "role", "FRONT",
                "protocol", "TROJAN",
                "serverAddr", "us.example.com",
                "port", 443,
                "extraConfig", Map.of("password", "偷偷塞进来的明文密码"),
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));

        assertThat(nodeRepository.findAll(null)).isEmpty();
    }

    @Test
    @DisplayName("删除不存在的节点报 410001；仍被用户引用的节点报 410003")
    void deleteNodeTwoFailureModes() throws Exception {
        mockMvc.perform(delete("/api/admin/nodes/99999").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410001));

        Long front = fixtures.createFrontNode("FRONT-1");
        Long land = fixtures.createLandNode("LAND-1", "203.0.113.10");
        fixtures.createUser("logto-user-1", front, land);

        mockMvc.perform(delete("/api/admin/nodes/" + land).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410003));
        mockMvc.perform(delete("/api/admin/nodes/" + front).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410003));
    }

    @Test
    @DisplayName("正被用户引用的节点改角色时报 410003，未被引用的节点可以正常改角色")
    void changingRoleOfReferencedNodeFails() throws Exception {
        Long front = fixtures.createFrontNode("FRONT-1");
        Long land = fixtures.createLandNode("LAND-1", "203.0.113.10");
        fixtures.createUser("logto-user-1", front, land);

        var changeToFront = Map.of(
                "name", "LAND-1", "role", "FRONT", "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10", "port", 50101,
                "egressIp", "203.0.113.10", "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + land).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(changeToFront)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410003));
        assertThat(nodeRepository.findById(land).orElseThrow().getRole()).isEqualTo(NodeRole.LAND);

        // 未被引用的空闲节点仍然可以正常改角色
        Long idle = fixtures.createLandNode("LAND-空闲", "203.0.113.20");
        var idleNodeChangeToFront = Map.of(
                "name", "LAND-空闲", "role", "FRONT", "protocol", "SOCKS5",
                "serverAddr", "203.0.113.20", "port", 50101,
                "egressIp", "203.0.113.20", "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + idle).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(idleNodeChangeToFront)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("空闲节点可以删除")
    void idleNodeCanBeDeleted() throws Exception {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");

        mockMvc.perform(delete("/api/admin/nodes/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("MIHOMO 协议不可手工新建，报参数错误 110001")
    void mihomoCannotBeCreatedManually() throws Exception {
        var body = Map.of("name", "X", "role", "FRONT", "protocol", "MIHOMO",
                "serverAddr", "hk.example.com", "port", 443, "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
        assertThat(nodeRepository.findAll(null)).isEmpty();
    }

    @Test
    @DisplayName("编辑 MIHOMO 节点只有名称/状态/备注生效，地址端口等参数改不动")
    void editingMihomoNodeAllowsOnlyThreeFields() throws Exception {
        Long id = fixtures.createMihomoNode("香港-01", null);
        var body = Map.of(
                "name", "香港-01-改名",
                "role", "FRONT",
                "protocol", "MIHOMO",
                "serverAddr", "evil.example.com",
                "port", 9999,
                "extraConfig", Map.of("sni", "evil"),
                "status", "DISABLED",
                "remark", "改了备注");

        mockMvc.perform(put("/api/admin/nodes/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        var node = nodeRepository.findById(id).orElseThrow();
        assertThat(node.getName()).isEqualTo("香港-01-改名");
        assertThat(node.getStatus()).isEqualTo(NodeStatus.DISABLED);
        assertThat(node.getRemark()).isEqualTo("改了备注");
        // 参数纹丝不动：展示列与加密参数都还是原值
        assertThat(node.getServerAddr()).isEqualTo("hk01.example.com");
        assertThat(node.getPort()).isEqualTo(35355);
        assertThat(node.getSecret()).containsEntry("password", "mihomo-密码");
        assertThat(node.getExtraConfig()).isEmpty();
    }

    @Test
    @DisplayName("把 MIHOMO 节点的协议改成别的协议，报参数错误 110001")
    void mihomoNodeCannotChangeProtocol() throws Exception {
        Long id = fixtures.createMihomoNode("香港-01", null);
        var body = Map.of("name", "香港-01", "role", "FRONT", "protocol", "TROJAN",
                "serverAddr", "hk01.example.com", "port", 35355, "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("非 MIHOMO 节点禁止改成 MIHOMO 协议，报参数错误 110001")
    void nonMihomoNodeCannotBecomeMihomo() throws Exception {
        Long id = fixtures.createFrontNode("FRONT-1");
        var body = Map.of("name", "FRONT-1", "role", "FRONT", "protocol", "MIHOMO",
                "serverAddr", "us.example.com", "port", 443, "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));

        assertThat(nodeRepository.findById(id).orElseThrow().getProtocol()).isEqualTo(NodeProtocol.TROJAN);
    }
}
