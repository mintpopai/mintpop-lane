package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.AirportSubscriptionCreateRequest;
import ai.mintpop.lane.request.AirportSubscriptionRenameRequest;
import ai.mintpop.lane.request.SubAuditRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.AirportSubscriptionResponse;
import ai.mintpop.lane.response.SubAuditResponse;
import ai.mintpop.lane.service.AdminAirportSubscriptionService;
import ai.mintpop.lane.service.SubAuditService;
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

/** 机场订阅管理与订阅导入。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/airport-subscriptions")
public class AdminAirportSubscriptionController {

    private final AdminAirportSubscriptionService adminAirportSubscriptionService;
    private final SubAuditService subAuditService;

    public AdminAirportSubscriptionController(AdminAirportSubscriptionService adminAirportSubscriptionService, SubAuditService subAuditService) {
        this.adminAirportSubscriptionService = adminAirportSubscriptionService;
        this.subAuditService = subAuditService;
    }

    /** 采购尽调：候选机场的试用订阅是否与库里已有节点撞故障域。只读，不落库 */
    @PostMapping("/audit")
    public ApiResponse<SubAuditResponse> audit(@Valid @RequestBody SubAuditRequest request) {
        return ApiResponse.success(subAuditService.audit(request.getSubUrl()));
    }

    @PostMapping
    public ApiResponse<Long> create(@Valid @RequestBody AirportSubscriptionCreateRequest request) {
        return ApiResponse.success(adminAirportSubscriptionService.create(request));
    }

    @GetMapping
    public ApiResponse<List<AirportSubscriptionResponse>> list() {
        return ApiResponse.success(adminAirportSubscriptionService.list());
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> rename(@PathVariable Long id, @Valid @RequestBody AirportSubscriptionRenameRequest request) {
        adminAirportSubscriptionService.rename(id, request);
        return ApiResponse.success();
    }

    /** 用保存的链接重新拉取，自动导入其中的美国节点 */
    @PostMapping("/{id}/import")
    public ApiResponse<Void> importNodes(@PathVariable Long id) {
        adminAirportSubscriptionService.importNodes(id);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminAirportSubscriptionService.delete(id);
        return ApiResponse.success();
    }
}
