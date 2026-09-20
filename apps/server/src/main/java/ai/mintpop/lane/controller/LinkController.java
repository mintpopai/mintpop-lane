package ai.mintpop.lane.controller;

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
            @RequestBody(required = false) String rawReport,
            HttpServletRequest httpRequest) {
        HeartbeatResponse response = linkService.heartbeat(userId);
        // 上报失败绝不能影响心跳本身：心跳承载的是「这个用户还能不能用」，观测数据丢一个
        // 窗口无所谓，把心跳搞挂会让客户端误判成链路失效、当场断链。
        //
        // 刻意接成原始字符串、不用 @RequestBody LinkHeartbeatRequest：那样 Spring 会在进入
        // 方法体之前就做 JSON 反序列化，语法错误/字段类型不匹配会被 GlobalExceptionHandler
        // 的 HttpMessageNotReadableException 分支接住，让整条心跳的 data 变成 null——与上面
        // 那条原则矛盾（客户端拿不到 status 会误判链路失效并断链），且这与加不加 @Valid
        // 无关（LinkHeartbeatRequest 本来就没有 Bean Validation 注解，反序列化失败发生在
        // 绑定阶段，早于任何校验）。解析挪到 LinkReportService#ingest 内部，
        // 和落库逻辑包进同一个 try/catch，才能保证任何格式问题都只丢一个窗口。
        if (rawReport != null && !rawReport.isBlank()) {
            linkReportService.ingest(userId, rawReport, clientIpOf(httpRequest));
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
