package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.dto.PageResult;
import ai.mintpop.lane.request.UserSaveRequest;
import ai.mintpop.lane.response.AdminUserResponse;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.FrontSubscriptionBrief;
import ai.mintpop.lane.service.AdminUserService;
import ai.mintpop.lane.service.FrontSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户管理。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final FrontSubscriptionService frontSubscriptionService;

    public AdminUserController(AdminUserService adminUserService, FrontSubscriptionService frontSubscriptionService) {
        this.adminUserService = adminUserService;
        this.frontSubscriptionService = frontSubscriptionService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean hasActiveSubscription,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(adminUserService.page(keyword, hasActiveSubscription, pageNo, pageSize));
    }

    /** 单个用户详情：管理端「用户订阅」独立页刷新后凭 URL 里的 id 重取用户信息 */
    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> get(@PathVariable Long id) {
        return ApiResponse.success(adminUserService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        adminUserService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id);
        return ApiResponse.success();
    }

    /** 按场景负载算法整份重算该用户的第一跳订阅列表 */
    @PostMapping("/{id}/front/allocate")
    public ApiResponse<List<FrontSubscriptionBrief>> allocateFront(@PathVariable Long id) {
        return ApiResponse.success(frontSubscriptionService.allocate(id));
    }

    /** 清空该用户的第一跳订阅列表 */
    @DeleteMapping("/{id}/front")
    public ApiResponse<Void> clearFront(@PathVariable Long id) {
        frontSubscriptionService.clear(id);
        return ApiResponse.success();
    }
}
