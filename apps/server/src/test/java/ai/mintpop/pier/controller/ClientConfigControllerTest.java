package ai.mintpop.pier.controller;

import ai.mintpop.pier.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本用例不需要数据库，继承 MysqlTestBase 只是为了复用同一个 Spring 测试上下文缓存，
 * 避免因配置不同而额外启一份上下文。
 */
@AutoConfigureMockMvc
class ClientConfigControllerTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("匿名可拿到引导配置，三个字段与配置一致")
    void 匿名可拿到引导配置() throws Exception {
        mockMvc.perform(get("/api/client-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logtoIssuer").value("https://tenant.test.example/oidc"))
                .andExpect(jsonPath("$.data.logtoClientId").value("test-client-id"))
                // 与鉴权配置的 audiences[0] 同源：改鉴权配置，下发值必须跟着变
                .andExpect(jsonPath("$.data.apiResource").value("https://api.pier.mintpop.test"));
    }

    @Test
    @DisplayName("放行只波及该端点，链路配置匿名访问仍被拒")
    void 放行不波及其它端点() throws Exception {
        mockMvc.perform(get("/api/link/config"))
                .andExpect(status().isUnauthorized());
    }
}
