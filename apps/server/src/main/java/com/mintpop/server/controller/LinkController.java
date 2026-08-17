package com.mintpop.server.controller;

import com.mintpop.server.response.ApiResponse;
import com.mintpop.server.response.HeartbeatResponse;
import com.mintpop.server.response.LinkConfigResponse;
import com.mintpop.server.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链路接口。
 * 员工身份取自 JWT 的 sub，客户端无法伪造，也无需在请求里自报身份。
 */
@Slf4j
@RestController
@RequestMapping("/api/link")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping("/config")
    public ApiResponse<LinkConfigResponse> config(@AuthenticationPrincipal Jwt jwt) {
        String subject = jwt.getSubject();
        log.info("下发链路配置，subject={}", subject);
        return ApiResponse.success(linkService.resolveLink(subject));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<HeartbeatResponse> heartbeat(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(linkService.heartbeat(jwt.getSubject()));
    }
}
