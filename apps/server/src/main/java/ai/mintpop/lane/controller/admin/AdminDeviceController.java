package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.response.AdminDeviceRebindRequestResponse;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.service.AdminDeviceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端的设备管理接口。权限由 SecurityConfig 的 /api/admin/** → ROLE_ADMIN 统一兜住，
 * 这里不再逐个方法标注。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminDeviceController {

    private final AdminDeviceService adminDeviceService;

    public AdminDeviceController(AdminDeviceService adminDeviceService) {
        this.adminDeviceService = adminDeviceService;
    }

    @GetMapping("/device-rebind-requests")
    public ApiResponse<List<AdminDeviceRebindRequestResponse>> list(
            @RequestParam(value = "status", required = false) RebindRequestStatus status) {
        return ApiResponse.success(adminDeviceService.listRebindRequests(status));
    }

    @PostMapping("/device-rebind-requests/{id}/approve")
    public ApiResponse<Void> approve(@PathVariable Long id,
                                     @AuthenticationPrincipal Long adminUserId) {
        log.info("同意换机申请，requestId={}，adminUserId={}", id, adminUserId);
        adminDeviceService.approve(id, adminUserId);
        return ApiResponse.success();
    }

    @PostMapping("/device-rebind-requests/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable Long id,
                                    @AuthenticationPrincipal Long adminUserId) {
        log.info("拒绝换机申请，requestId={}，adminUserId={}", id, adminUserId);
        adminDeviceService.reject(id, adminUserId);
        return ApiResponse.success();
    }

    @PostMapping("/subscriptions/{id}/device/unbind")
    public ApiResponse<Void> unbind(@PathVariable Long id,
                                    @AuthenticationPrincipal Long adminUserId) {
        log.info("强制解绑订阅设备，subscriptionId={}，adminUserId={}", id, adminUserId);
        adminDeviceService.unbind(id);
        return ApiResponse.success();
    }
}
