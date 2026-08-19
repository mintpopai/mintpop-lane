package ai.mintpop.lane.controller;

import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static ai.mintpop.lane.enumeration.UserStatus.REVOKED;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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

    @BeforeEach
    void 准备数据() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository);
        fixtures.清空();
        Long front = fixtures.建FRONT节点("FRONT-1");
        Long land1 = fixtures.建LAND节点("LAND-1", "77.47.143.6");
        Long land2 = fixtures.建LAND节点("LAND-2", "8.8.8.8");
        fixtures.建用户("logto-user-1", MEMBER, ACTIVE, front, land1, "sk-ant-test-1");
        fixtures.建用户("logto-user-2", MEMBER, REVOKED, front, land2, "sk-ant-test-2");
    }

    @Test
    @DisplayName("无令牌访问接口被拒")
    void 无令牌访问接口被拒() throws Exception {
        mockMvc.perform(get("/api/link/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常用户拿到链路配置，业务码为 0")
    void 正常用户拿到链路配置() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .with(jwt().jwt(j -> j.subject("logto-user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.front.type").value("trojan"))
                .andExpect(jsonPath("$.data.land.server").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.expectedEgressIps[0]").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.claudeCredential").value("sk-ant-test-1"));
    }

    @Test
    @DisplayName("已吊销用户拿不到链路，HTTP 仍为 200 但业务码非 0")
    void 已吊销用户拿不到链路() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .with(jwt().jwt(j -> j.subject("logto-user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(310003))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("未录入账号拿不到链路")
    void 未录入账号拿不到链路() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .with(jwt().jwt(j -> j.subject("陌生人"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(210003));
    }

    @Test
    @DisplayName("心跳返回用户当前状态")
    void 心跳返回用户当前状态() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .with(jwt().jwt(j -> j.subject("logto-user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("已吊销用户的心跳返回 REVOKED")
    void 已吊销用户的心跳返回吊销() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .with(jwt().jwt(j -> j.subject("logto-user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }
}
