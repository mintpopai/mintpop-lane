package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.enumeration.UserRole;
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

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 可变 Map：部分用例要放 null 值，Map.of 不允许 */
    private Map<String, Object> 入参(String subject, Long front, Long land) {
        Map<String, Object> body = new HashMap<>();
        body.put("subject", subject);
        body.put("email", subject == null ? null : subject + "@test.example");
        body.put("name", "张三");
        body.put("status", "ACTIVE");
        body.put("frontNodeId", front);
        body.put("landNodeId", land);
        return body;
    }

    @BeforeEach
    void 准备() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.清空();
        frontId = fixtures.建FRONT节点("FRONT-1");
        landId = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        adminId = fixtures.建用户("logto-admin", ADMIN, ACTIVE, null, null);
    }

    @Test
    @DisplayName("新建用户并在列表里看到节点名与出口 IP")
    void 新建用户并列出() throws Exception {
        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, landId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());

        // 加关键字过滤掉 @BeforeEach 里造的管理员账号，避免断言与夹具耦合
        mockMvc.perform(get("/api/admin/users").param("keyword", "logto-user-1")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-user-1"))
                .andExpect(jsonPath("$.data.records[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.records[0].frontNodeName").value("FRONT-1"))
                .andExpect(jsonPath("$.data.records[0].landNodeName").value("LAND-1"))
                .andExpect(jsonPath("$.data.records[0].egressIps[0]").value("203.0.113.10"));
    }

    @Test
    @DisplayName("关键字搜索命中姓名或 subject")
    void 关键字搜索() throws Exception {
        fixtures.建用户("logto-user-1", frontId, landId);
        fixtures.建用户("logto-user-2", frontId, null);

        mockMvc.perform(get("/api/admin/users").param("keyword", "user-2").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-user-2"));
    }

    @Test
    @DisplayName("同一个 Logto 账号重复录入报 410004")
    void 重复录入报错() throws Exception {
        fixtures.建用户("logto-user-1", frontId, null);

        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, null))))
                .andExpect(jsonPath("$.code").value(410004));
    }

    @Test
    @DisplayName("落地节点已被占用时报 410002")
    void 落地被占用时报错() throws Exception {
        fixtures.建用户("logto-user-1", frontId, landId);

        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-2", frontId, landId))))
                .andExpect(jsonPath("$.code").value(410002));
    }

    @Test
    @DisplayName("节点不存在报 410001，节点角色用错报 410005")
    void 节点校验() throws Exception {
        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", 99999L, null))))
                .andExpect(jsonPath("$.code").value(410001));

        // 把落地节点当第一跳用
        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", landId, null))))
                .andExpect(jsonPath("$.code").value(410005));

        // 把第一跳节点当落地用
        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, frontId))))
                .andExpect(jsonPath("$.code").value(410005));
    }

    @Test
    @DisplayName("更新能取消落地分配，释放出来的落地可以给别人")
    void 更新能取消落地分配() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        mockMvc.perform(put("/api/admin/users/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(id).orElseThrow().getLandNodeId()).isNull();

        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-2", frontId, landId))))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("更新时把落地改回自己名下不报占用")
    void 更新时保留原落地不报占用() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);
        Map<String, Object> body = 入参("logto-user-1", frontId, landId);
        body.put("status", "SUSPENDED");

        mockMvc.perform(put("/api/admin/users/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("角色不能通过接口修改：请求体里塞 role 也不生效")
    void 角色不能通过接口修改() throws Exception {
        Map<String, Object> body = 入参("logto-user-1", frontId, null);
        body.put("role", "ADMIN");

        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findBySubject("logto-user-1").orElseThrow().getRole())
                .isEqualTo(UserRole.MEMBER);
    }

    @Test
    @DisplayName("更新一个 ADMIN 用户时，请求体里塞 role: MEMBER 也不能把它降级")
    void 更新不能把管理员降级() throws Exception {
        Long id = fixtures.建用户("logto-admin-1", UserRole.ADMIN, UserStatus.ACTIVE, frontId, null);

        Map<String, Object> body = 入参("logto-admin-1", frontId, null);
        body.put("name", "改过的姓名");
        body.put("role", "MEMBER");

        mockMvc.perform(put("/api/admin/users/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        var user = userRepository.findById(id).orElseThrow();
        assertThat(user.getName()).isEqualTo("改过的姓名");
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("对不存在的用户做更新或删除报 410006")
    void 用户不存在时报错() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, null))))
                .andExpect(jsonPath("$.code").value(410006));

        mockMvc.perform(delete("/api/admin/users/99999").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410006));
    }

    @Test
    @DisplayName("删除用户后其落地出口即释放")
    void 删除用户后落地释放() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        mockMvc.perform(delete("/api/admin/users/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(id)).isEmpty();
        assertThat(userRepository.findByLandNodeId(landId)).isEmpty();
    }

    @Test
    @DisplayName("必填项缺失时报参数错误 110001")
    void 必填项缺失时报参数错误() throws Exception {
        Map<String, Object> body = 入参(null, frontId, null);

        mockMvc.perform(post("/api/admin/users").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(110001));
    }
}
