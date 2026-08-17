package com.mintpop.server.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LinkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("无令牌访问接口被拒")
    void 无令牌访问接口被拒() throws Exception {
        mockMvc.perform(get("/api/link/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常员工拿到链路配置，业务码为 0")
    void 正常员工拿到链路配置() throws Exception {
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
    @DisplayName("已吊销员工拿不到链路，HTTP 仍为 200 但业务码非 0")
    void 已吊销员工拿不到链路() throws Exception {
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
    @DisplayName("心跳返回员工当前状态")
    void 心跳返回员工当前状态() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .with(jwt().jwt(j -> j.subject("logto-user-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("已吊销员工的心跳返回 REVOKED")
    void 已吊销员工的心跳返回吊销() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .with(jwt().jwt(j -> j.subject("logto-user-2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }
}
