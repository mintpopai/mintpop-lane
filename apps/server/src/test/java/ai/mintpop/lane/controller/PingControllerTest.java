package ai.mintpop.lane.controller;

import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/ping} 是客户端 mihomo fallback 组用来判断链路通不通的靶子，必须匿名可达。
 * standaloneSetup 会绕开真实的 {@link ai.mintpop.lane.config.SecurityConfig}，
 * 测不出「放行规则真的生效」；这里复用 {@link MysqlTestBase} 起完整 Spring 安全链，
 * 才能验证「不带任何凭据也能通过」这一点。
 */
@AutoConfigureMockMvc
class PingControllerTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/ping 不带任何凭据也返回 204")
    void pingIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("/api/ping 不返回任何响应体——它只是健康检查靶子")
    void pingHasEmptyBody() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(content().string(""));
    }
}
