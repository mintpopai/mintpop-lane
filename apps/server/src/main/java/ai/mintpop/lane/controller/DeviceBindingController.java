package ai.mintpop.lane.controller;

import ai.mintpop.lane.request.DeviceBindRequest;
import ai.mintpop.lane.request.DeviceRebindCreateRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.service.DeviceBindingService;
import ai.mintpop.lane.service.DeviceRebindNotifyService;
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
 */
@Slf4j
@RestController
@RequestMapping("/api/subscriptions/{id}/device")
public class DeviceBindingController {

    private final DeviceBindingService deviceBindingService;
    private final DeviceRebindNotifyService deviceRebindNotifyService;

    public DeviceBindingController(DeviceBindingService deviceBindingService,
                                   DeviceRebindNotifyService deviceRebindNotifyService) {
        this.deviceBindingService = deviceBindingService;
        this.deviceRebindNotifyService = deviceRebindNotifyService;
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
        Long requestId = deviceBindingService.requestRebind(
                userId, subscriptionId, DeviceId.normalize(deviceId), body);
        // 通知调用必须留在这里、不能挪进 requestRebind 内部：requestRebind 是 @Transactional，
        // 事务要等它返回才真正提交；在这里调用时事务已提交，@Async 通知重查必见那一行。
        // 挪进服务内部会让通知在事务仍未提交时就可能跑起来、查库查不到，只在高并发下偶发出现。
        deviceRebindNotifyService.notifyRebindRequested(requestId);
        return ApiResponse.success();
    }
}
