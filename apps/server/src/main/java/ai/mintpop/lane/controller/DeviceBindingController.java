package ai.mintpop.lane.controller;

import ai.mintpop.lane.request.DeviceBindRequest;
import ai.mintpop.lane.request.DeviceRebindCreateRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.service.DeviceBindingService;
import ai.mintpop.lane.util.DeviceId;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧的设备绑定接口。身份取自会话 token，设备取自 X-Device-Id 请求头，两者都不由请求体自报。
 *
 * <p>换机申请提交后的通知推送（飞书卡片）是 Task 6 的职责，本接口只负责把申请落库。
 */
@Slf4j
@RestController
@RequestMapping("/api/subscriptions/{id}/device")
public class DeviceBindingController {

    private final DeviceBindingService deviceBindingService;

    public DeviceBindingController(DeviceBindingService deviceBindingService) {
        this.deviceBindingService = deviceBindingService;
    }

    @PostMapping("/bind")
    public ApiResponse<Void> bind(@AuthenticationPrincipal Long userId,
                                  @PathVariable("id") Long subscriptionId,
                                  @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                  @Valid @RequestBody DeviceBindRequest body) {
        // 形状校验与归一化收在 DeviceId 一处，下沉到服务层的必须是归一化之后的值——
        // upsert 按原样存，LinkServiceImpl 按归一化值匹配，两边不一致会让绑定在两个大小写间悄悄错位
        deviceBindingService.bind(userId, subscriptionId, DeviceId.normalize(deviceId), body);
        return ApiResponse.success();
    }

    @PostMapping("/rebind-requests")
    public ApiResponse<Void> requestRebind(@AuthenticationPrincipal Long userId,
                                           @PathVariable("id") Long subscriptionId,
                                           @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
                                           @Valid @RequestBody DeviceRebindCreateRequest body) {
        deviceBindingService.requestRebind(userId, subscriptionId, DeviceId.normalize(deviceId), body);
        return ApiResponse.success();
    }
}
