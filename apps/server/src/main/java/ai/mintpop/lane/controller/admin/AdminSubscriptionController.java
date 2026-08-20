package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.SubscriptionSaveRequest;
import ai.mintpop.lane.response.AdminSubscriptionResponse;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.service.AdminSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 订阅管理。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin")
public class AdminSubscriptionController {

    private final AdminSubscriptionService adminSubscriptionService;

    public AdminSubscriptionController(AdminSubscriptionService adminSubscriptionService) {
        this.adminSubscriptionService = adminSubscriptionService;
    }

    @GetMapping("/users/{userId}/subscriptions")
    public ApiResponse<List<AdminSubscriptionResponse>> list(@PathVariable Long userId) {
        return ApiResponse.success(adminSubscriptionService.listByUser(userId));
    }

    @PostMapping("/users/{userId}/subscriptions")
    public ApiResponse<Long> create(@PathVariable Long userId,
                                    @Valid @RequestBody SubscriptionSaveRequest request) {
        return ApiResponse.success(adminSubscriptionService.create(userId, request));
    }

    @PutMapping("/subscriptions/{id}")
    public ApiResponse<Void> update(@PathVariable Long id,
                                    @Valid @RequestBody SubscriptionSaveRequest request) {
        adminSubscriptionService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/subscriptions/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminSubscriptionService.delete(id);
        return ApiResponse.success();
    }
}
