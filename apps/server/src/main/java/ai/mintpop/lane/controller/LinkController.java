package ai.mintpop.lane.controller;

import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import ai.mintpop.lane.service.LinkService;
import ai.mintpop.lane.util.DeviceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<LinkConfigResponse> config(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        // 形状校验收在 DeviceId 一处；缺头或形状不对都抛 DEVICE_ID_MISSING，
        // 对客户端来说这两种情况该做的事一样：升级
        String normalized = DeviceId.normalize(deviceId);
        log.info("下发链路配置，userId={}", userId);
        return ApiResponse.success(linkService.resolveLink(userId, normalized));
    }

    @PostMapping("/heartbeat")
    public ApiResponse<HeartbeatResponse> heartbeat(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(linkService.heartbeat(userId));
    }
}
