package com.mintpop.server.config;

import com.mintpop.server.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAuthorizationTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("无令牌访问管理端接口得 401")
    void 无令牌访问管理端接口得401() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("普通成员访问管理端接口得 403")
    void 普通成员访问管理端接口得403() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("没有任何权限的令牌（库里查无此人）访问管理端接口得 403")
    void 无权限令牌访问管理端接口得403() throws Exception {
        mockMvc.perform(get("/api/admin/nodes").with(jwt().authorities()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员能通过授权关卡访问管理端接口")
    void 管理员通过授权关卡() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
