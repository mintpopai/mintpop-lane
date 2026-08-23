package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.PlanSaveRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.PlanResponse;
import ai.mintpop.lane.service.AdminPlanService;
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

/** 套餐维护。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/plans")
public class AdminPlanController {

    private final AdminPlanService adminPlanService;

    public AdminPlanController(AdminPlanService adminPlanService) {
        this.adminPlanService = adminPlanService;
    }

    @GetMapping
    public ApiResponse<List<PlanResponse>> list() {
        return ApiResponse.success(adminPlanService.list());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody PlanSaveRequest request) {
        return ApiResponse.success(adminPlanService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody PlanSaveRequest request) {
        adminPlanService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminPlanService.delete(id);
        return ApiResponse.success();
    }
}
