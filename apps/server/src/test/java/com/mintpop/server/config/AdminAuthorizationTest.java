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

    // 接口尚未实现（本任务不涉及 /api/admin/nodes 的业务实现），期望它拿到 404 而非
    // 403，以此证明请求已经通过了授权关卡。但实测拿到的是 HTTP 200——GlobalExceptionHandler
    // 的 @ExceptionHandler(Exception.class) 兜底捕获了框架级的 NoResourceFoundException，
    // 按 api-response.md 规范把它转成了 200 + 错误码的 ApiResponse。这个兜底处理器不在本任务
    // 声明的改动范围内，不应为了让这条用例断言到字面 404 而顺手去改它；按任务书 Step 6 给的
    // 退路处理：先禁用，Task 7 把 /api/admin/nodes 实现后改回真实断言（届时应为 200）。
    @org.junit.jupiter.api.Disabled("接口在 Task 7 实现后改为断言 200；当前因 GlobalExceptionHandler 兜底捕获 "
            + "NoResourceFoundException 而返回 200(code=110002)，非 404/403，与安全规则无关，见类注释")
    @Test
    @DisplayName("管理员通过授权关卡——接口尚未实现，因此得到的是 404 而不是 403")
    void 管理员通过授权关卡() throws Exception {
        mockMvc.perform(get("/api/admin/nodes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}
