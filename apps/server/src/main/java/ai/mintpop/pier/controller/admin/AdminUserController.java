package ai.mintpop.pier.controller.admin;

import ai.mintpop.pier.dto.PageResult;
import ai.mintpop.pier.request.UserSaveRequest;
import ai.mintpop.pier.response.AdminUserResponse;
import ai.mintpop.pier.response.ApiResponse;
import ai.mintpop.pier.service.AdminUserService;
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

/** 用户管理。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.success(adminUserService.page(keyword, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody UserSaveRequest request) {
        return ApiResponse.success(adminUserService.create(request));
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
}
