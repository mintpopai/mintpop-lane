package com.mintpop.server.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mintpop.server.enumeration.UserRole;
import com.mintpop.server.repository.ProxyNodeRepository;
import com.mintpop.server.repository.UserRepository;
import com.mintpop.server.support.DatabaseFixtures;
import com.mintpop.server.support.MysqlTestBase;
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

import java.util.HashMap;
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

    private DatabaseFixtures fixtures;
    private Long frontId;
    private Long landId;

    private static RequestPostProcessor 管理员() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 可变 Map：部分用例要放 null 值，Map.of 不允许 */
    private Map<String, Object> 入参(String subject, Long front, Long land, String credential) {
        Map<String, Object> body = new HashMap<>();
        body.put("subject", subject);
        body.put("name", "张三");
        body.put("status", "ACTIVE");
        body.put("frontNodeId", front);
        body.put("landNodeId", land);
        body.put("claudeCredential", credential);
        return body;
    }

    @BeforeEach
    void 准备() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository);
        fixtures.清空();
        frontId = fixtures.建FRONT节点("FRONT-1");
        landId = fixtures.建LAND节点("LAND-1", "203.0.113.10");
    }

    @Test
    @DisplayName("新建用户并在列表里看到节点名与出口 IP，凭据不回传")
    void 新建用户并列出() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, landId, "sk-ant-真凭据"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/admin/users").with(管理员()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-user-1"))
                .andExpect(jsonPath("$.data.records[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.data.records[0].frontNodeName").value("FRONT-1"))
                .andExpect(jsonPath("$.data.records[0].landNodeName").value("LAND-1"))
                .andExpect(jsonPath("$.data.records[0].egressIps[0]").value("203.0.113.10"))
                .andExpect(jsonPath("$.data.records[0].credentialConfigured").value(true))
                // 凭据一个字符都不许回传
                .andExpect(jsonPath("$.data.records[0].claudeCredential").doesNotExist());
    }

    @Test
    @DisplayName("关键字搜索命中姓名或 subject")
    void 关键字搜索() throws Exception {
        fixtures.建用户("logto-user-1", frontId, landId);
        fixtures.建用户("logto-user-2", frontId, null);

        mockMvc.perform(get("/api/admin/users").param("keyword", "user-2").with(管理员()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subject").value("logto-user-2"));
    }

    @Test
    @DisplayName("同一个 Logto 账号重复录入报 410004")
    void 重复录入报错() throws Exception {
        fixtures.建用户("logto-user-1", frontId, null);

        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, null, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(410004));
    }

    @Test
    @DisplayName("落地节点已被占用时报 410002")
    void 落地被占用时报错() throws Exception {
        fixtures.建用户("logto-user-1", frontId, landId);

        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-2", frontId, landId, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(410002));
    }

    @Test
    @DisplayName("节点不存在报 410001，节点角色用错报 410005")
    void 节点校验() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", 99999L, null, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(410001));

        // 把落地节点当第一跳用
        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", landId, null, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(410005));

        // 把第一跳节点当落地用
        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, frontId, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(410005));
    }

    @Test
    @DisplayName("更新时凭据留空表示不改")
    void 更新时凭据留空表示不改() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);
        Map<String, Object> body = 入参("logto-user-1", frontId, landId, "   ");
        body.put("name", "李四");

        mockMvc.perform(put("/api/admin/users/" + id).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        var user = userRepository.findById(id).orElseThrow();
        assertThat(user.getName()).isEqualTo("李四");
        assertThat(user.getClaudeCredential()).isEqualTo("sk-ant-logto-user-1");
    }

    @Test
    @DisplayName("更新能取消落地分配，释放出来的落地可以给别人")
    void 更新能取消落地分配() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        mockMvc.perform(put("/api/admin/users/" + id).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, null, null))))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(id).orElseThrow().getLandNodeId()).isNull();

        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-2", frontId, landId, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("更新时把落地改回自己名下不报占用")
    void 更新时保留原落地不报占用() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);
        Map<String, Object> body = 入参("logto-user-1", frontId, landId, null);
        body.put("status", "SUSPENDED");

        mockMvc.perform(put("/api/admin/users/" + id).with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("角色不能通过接口修改：请求体里塞 role 也不生效")
    void 角色不能通过接口修改() throws Exception {
        Map<String, Object> body = 入参("logto-user-1", frontId, null, "sk-ant-x");
        body.put("role", "ADMIN");

        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findBySubject("logto-user-1").orElseThrow().getRole())
                .isEqualTo(UserRole.MEMBER);
    }

    @Test
    @DisplayName("对不存在的用户做更新或删除报 410006")
    void 用户不存在时报错() throws Exception {
        mockMvc.perform(put("/api/admin/users/99999").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(入参("logto-user-1", frontId, null, "sk-ant-x"))))
                .andExpect(jsonPath("$.code").value(410006));

        mockMvc.perform(delete("/api/admin/users/99999").with(管理员()))
                .andExpect(jsonPath("$.code").value(410006));
    }

    @Test
    @DisplayName("删除用户后其落地出口即释放")
    void 删除用户后落地释放() throws Exception {
        Long id = fixtures.建用户("logto-user-1", frontId, landId);

        mockMvc.perform(delete("/api/admin/users/" + id).with(管理员()))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(userRepository.findById(id)).isEmpty();
        assertThat(userRepository.findByLandNodeId(landId)).isEmpty();
    }

    @Test
    @DisplayName("必填项缺失时报参数错误 110001")
    void 必填项缺失时报参数错误() throws Exception {
        Map<String, Object> body = 入参(null, frontId, null, "sk-ant-x");

        mockMvc.perform(post("/api/admin/users").with(管理员())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(110001));
    }
}
