package ai.mintpop.lane.controller;

import ai.mintpop.lane.request.LinkHeartbeatRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import ai.mintpop.lane.service.LinkReportService;
import ai.mintpop.lane.service.LinkService;
import ai.mintpop.lane.util.DeviceId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final LinkReportService linkReportService;

    public LinkController(LinkService linkService, LinkReportService linkReportService) {
        this.linkService = linkService;
        this.linkReportService = linkReportService;
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
    public ApiResponse<HeartbeatResponse> heartbeat(
            @AuthenticationPrincipal Long userId,
            @RequestBody(required = false) LinkHeartbeatRequest report,
            HttpServletRequest httpRequest) {
        HeartbeatResponse response = linkService.heartbeat(userId);
        // 上报失败绝不能影响心跳本身：心跳承载的是「这个用户还能不能用」，观测数据丢一个
        // 窗口无所谓，把心跳搞挂会让客户端误判成链路失效、当场断链。校验与异常兜底全收在
        // LinkReportService#ingest 内部；这里刻意不给 report 加 @Valid——Bean Validation
        // 校验失败会在进入方法体之前就把整个心跳请求变成参数错误响应（见
        // GlobalExceptionHandler 对 MethodArgumentNotValidException 的处理，data 整体变
        // null），与这条原则矛盾，格式有问题的上报块交给 ingest 内部的 try/catch 兜底丢弃。
        if (report != null) {
            linkReportService.ingest(userId, report, clientIpOf(httpRequest));
        }
        return ApiResponse.success(response);
    }

    /** 取上报请求的来源 IP：服务端在反代之后，只信 X-Forwarded-For 的第一段；缺头时回落 remote addr */
    private String clientIpOf(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
