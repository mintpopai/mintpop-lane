package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.EnterpriseSaveRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.EnterpriseResponse;
import ai.mintpop.lane.service.AdminEnterpriseService;
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

/** 企业维护。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/enterprises")
public class AdminEnterpriseController {

    private final AdminEnterpriseService adminEnterpriseService;

    public AdminEnterpriseController(AdminEnterpriseService adminEnterpriseService) {
        this.adminEnterpriseService = adminEnterpriseService;
    }

    @GetMapping
    public ApiResponse<List<EnterpriseResponse>> list() {
        return ApiResponse.success(adminEnterpriseService.list());
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody EnterpriseSaveRequest request) {
        return ApiResponse.success(adminEnterpriseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody EnterpriseSaveRequest request) {
        adminEnterpriseService.update(id, request);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminEnterpriseService.delete(id);
        return ApiResponse.success();
    }
}
