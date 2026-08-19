package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

    private DatabaseFixtures fixtures;

    /** 管理员身份。角色到权限的映射另有单测覆盖，这里直接给权限。 */
    private static RequestPostProcessor 管理员() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @BeforeEach
    void 准备() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.清空();
    }

    @Test
    @DisplayName("新建节点并在列表里看到它，敏感键只返回「已配置」标记")
    void 新建节点并列出() throws Exception {
        var body = Map.of(
                "name", "LAND-东京-03",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "extraConfig", Map.of("udp", true),
                "secret", Map.of("username", "u1", "password", "落地密码"),
                "egressIps", List.of("203.0.113.10"),
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/admin/nodes").with(管理员()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].name").value("LAND-东京-03"))
                .andExpect(jsonPath("$.data[0].protocol").value("SOCKS5"))
                .andExpect(jsonPath("$.data[0].extraConfig.udp").value(true))
                .andExpect(jsonPath("$.data[0].egressIps[0]").value("203.0.113.10"))
                .andExpect(jsonPath("$.data[0].secretConfigured").value(true))
                // 密码一个字符都不许回传
                .andExpect(jsonPath("$.data[0].secret").doesNotExist())
                .andExpect(jsonPath("$.data[0].assignedUserName").doesNotExist());
    }

    @Test
    @DisplayName("按角色过滤节点列表")
    void 按角色过滤节点列表() throws Exception {
        fixtures.建FRONT节点("FRONT-1");
        fixtures.建LAND节点("LAND-1", "203.0.113.10");

        mockMvc.perform(get("/api/admin/nodes").param("role", "FRONT").with(管理员()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].role").value("FRONT"));
    }

    @Test
    @DisplayName("落地节点已分配时列表里显示占用者")
    void 落地节点显示占用者() throws Exception {
        Long front = fixtures.建FRONT节点("FRONT-1");
        Long land = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        fixtures.建用户("logto-user-1", front, land);

        mockMvc.perform(get("/api/admin/nodes").param("role", "LAND").with(管理员()))
                .andExpect(jsonPath("$.data[0].assignedUserName").value("测试logto-user-1"));
    }

    @Test
    @DisplayName("更新节点时敏感键留空表示不改")
    void 更新节点时敏感键留空表示不改() throws Exception {
        Long id = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        var body = Map.of(
                "name", "LAND-1-改名",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "egressIps", List.of("203.0.113.10"),
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id).orElseThrow().getSecret())
                .containsEntry("password", "land-密码");
        assertThat(nodeRepository.findById(id).orElseThrow().getName()).isEqualTo("LAND-1-改名");
    }

    @Test
    @DisplayName("更新节点改名撞上其它节点的名字时报 410007")
    void 更新节点改名冲突时报错() throws Exception {
        Long a = fixtures.建FRONT节点("FRONT-A");
        Long b = fixtures.建LAND节点("LAND-B", "203.0.113.10");
        var body = Map.of(
                "name", "FRONT-A",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50101,
                "egressIps", List.of("203.0.113.10"),
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + b).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410007));

        assertThat(nodeRepository.findById(a).orElseThrow().getName()).isEqualTo("FRONT-A");
        assertThat(nodeRepository.findById(b).orElseThrow().getName()).isEqualTo("LAND-B");
    }

    @Test
    @DisplayName("更新节点时把名字改成它自己原来的名字，不误报重复")
    void 更新节点改成自己原名不误报重复() throws Exception {
        Long id = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        var body = Map.of(
                "name", "LAND-1",
                "role", "LAND",
                "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10",
                "port", 50102,
                "egressIps", List.of("203.0.113.10"),
                "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + id).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id).orElseThrow().getPort()).isEqualTo(50102);
    }

    @Test
    @DisplayName("节点名重复时报 410007")
    void 节点名重复时报错() throws Exception {
        fixtures.建FRONT节点("FRONT-1");
        var body = Map.of("name", "FRONT-1", "role", "FRONT", "protocol", "TROJAN",
                "serverAddr", "us.example.com", "port", 443, "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410007));
    }

    @Test
    @DisplayName("端口非法时报参数错误 110001，而不是 500")
    void 端口非法时报参数错误() throws Exception {
        var body = Map.of("name", "X", "role", "FRONT", "protocol", "TROJAN",
                "serverAddr", "us.example.com", "port", 70000, "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("角色查询参数取值非法（枚举转换失败）时报参数错误 110001，而不是 500")
    void 角色查询参数非法时报参数错误() throws Exception {
        mockMvc.perform(get("/api/admin/nodes").param("role", "不存在的角色").with(管理员()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("请求体里字段类型错（port 传字符串）时报参数错误 110001，而不是内部错误 110002")
    void 请求体字段类型错时报参数错误() throws Exception {
        String body = """
                {"name":"X","role":"FRONT","protocol":"TROJAN",
                 "serverAddr":"us.example.com","port":"abc","status":"ENABLED"}""";

        mockMvc.perform(post("/api/admin/nodes").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("敏感键混进 extraConfig 时报参数错误 110001，且不会明文落库")
    void 敏感键混进extraConfig时报错() throws Exception {
        var body = Map.of(
                "name", "FRONT-混入密码",
                "role", "FRONT",
                "protocol", "TROJAN",
                "serverAddr", "us.example.com",
                "port", 443,
                "extraConfig", Map.of("password", "偷偷塞进来的明文密码"),
                "status", "ENABLED");

        mockMvc.perform(post("/api/admin/nodes").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(110001));

        assertThat(nodeRepository.findAll(null)).isEmpty();
    }

    @Test
    @DisplayName("删除不存在的节点报 410001；仍被用户引用的节点报 410003")
    void 删除节点的两种失败() throws Exception {
        mockMvc.perform(delete("/api/admin/nodes/99999").with(管理员()))
                .andExpect(jsonPath("$.code").value(410001));

        Long front = fixtures.建FRONT节点("FRONT-1");
        Long land = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        fixtures.建用户("logto-user-1", front, land);

        mockMvc.perform(delete("/api/admin/nodes/" + land).with(管理员()))
                .andExpect(jsonPath("$.code").value(410003));
        mockMvc.perform(delete("/api/admin/nodes/" + front).with(管理员()))
                .andExpect(jsonPath("$.code").value(410003));
    }

    @Test
    @DisplayName("正被用户引用的节点改角色时报 410003，未被引用的节点可以正常改角色")
    void 被引用的节点改角色时报错() throws Exception {
        Long front = fixtures.建FRONT节点("FRONT-1");
        Long land = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        fixtures.建用户("logto-user-1", front, land);

        var 改成FRONT = Map.of(
                "name", "LAND-1", "role", "FRONT", "protocol", "SOCKS5",
                "serverAddr", "203.0.113.10", "port", 50101,
                "egressIps", List.of("203.0.113.10"), "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + land).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(改成FRONT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410003));
        assertThat(nodeRepository.findById(land).orElseThrow().getRole()).isEqualTo(NodeRole.LAND);

        // 未被引用的空闲节点仍然可以正常改角色
        Long idle = fixtures.建LAND节点("LAND-空闲", "203.0.113.20");
        var 空闲节点改成FRONT = Map.of(
                "name", "LAND-空闲", "role", "FRONT", "protocol", "SOCKS5",
                "serverAddr", "203.0.113.20", "port", 50101,
                "egressIps", List.of("203.0.113.20"), "status", "ENABLED");

        mockMvc.perform(put("/api/admin/nodes/" + idle).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(空闲节点改成FRONT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("空闲节点可以删除")
    void 空闲节点可以删除() throws Exception {
        Long id = fixtures.建LAND节点("LAND-1", "203.0.113.10");

        mockMvc.perform(delete("/api/admin/nodes/" + id).with(管理员()))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findById(id)).isEmpty();
    }
}
