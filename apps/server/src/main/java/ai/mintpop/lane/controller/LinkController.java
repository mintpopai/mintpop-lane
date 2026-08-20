package ai.mintpop.lane.controller;

import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import ai.mintpop.lane.service.LinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链路接口。
 * 用户身份取自会话 token 的 userid，客户端无法伪造，也无需在请求里自报身份。
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
    public ApiResponse<LinkConfigResponse> config(@AuthenticationPrincipal Long userId) {
        log.info("下发链路配置，userId={}", userId);
        return ApiResponse.success(linkService.resolveLink(userId));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<HeartbeatResponse> heartbeat(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(linkService.heartbeat(userId));
    }
}
