package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.LinkHealthResponse;
import ai.mintpop.lane.service.AdminLinkHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端「链路健康」查询：故障域 × 运营商成功率矩阵 + 入口 IP 变更时间线。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/link-health")
public class AdminLinkHealthController {

    private final AdminLinkHealthService adminLinkHealthService;

    public AdminLinkHealthController(AdminLinkHealthService adminLinkHealthService) {
        this.adminLinkHealthService = adminLinkHealthService;
    }

    @GetMapping
    public ApiResponse<LinkHealthResponse> get(@RequestParam(defaultValue = "7") int days) {
        return ApiResponse.success(adminLinkHealthService.getLinkHealth(days));
    }
}
