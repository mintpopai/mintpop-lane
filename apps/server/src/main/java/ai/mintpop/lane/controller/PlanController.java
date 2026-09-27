package ai.mintpop.lane.controller;

import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.UserPlanResponse;
import ai.mintpop.lane.service.PlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户侧套餐列表。/api/** 登录即可访问（任意角色），由 SecurityConfig 统一保证 */
@RestController
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/api/plans")
    public ApiResponse<List<UserPlanResponse>> list() {
        return ApiResponse.success(planService.listEnabled());
    }
}
