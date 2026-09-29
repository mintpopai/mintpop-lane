package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.FrontRebuildPreview;
import ai.mintpop.lane.response.FrontRebuildStatus;
import ai.mintpop.lane.service.FrontRebuildService;
import ai.mintpop.lane.service.SystemSettingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 全体用户线路重算。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/front/rebuild")
public class AdminFrontRebuildController {

    private final FrontRebuildService frontRebuildService;
    private final SystemSettingService systemSettingService;

    public AdminFrontRebuildController(FrontRebuildService frontRebuildService, SystemSettingService systemSettingService) {
        this.frontRebuildService = frontRebuildService;
        this.systemSettingService = systemSettingService;
    }

    @PostMapping
    public ApiResponse<Void> start() {
        frontRebuildService.start();
        return ApiResponse.success();
    }

    @GetMapping
    public ApiResponse<FrontRebuildStatus> status() {
        return ApiResponse.success(frontRebuildService.status());
    }

    /** 预检按表单里的新值算：地区决定候选订阅，每人带宽决定名额；机场数不影响主用名额，沿用现值 */
    @GetMapping("/preview")
    public ApiResponse<FrontRebuildPreview> preview(@RequestParam NodeRegion region,
                                                    @RequestParam int bandwidthPerUserMbps) {
        FrontSettings current = systemSettingService.frontSettings();
        return ApiResponse.success(frontRebuildService.preview(
                new FrontSettings(region, current.airportsPerUser(), bandwidthPerUserMbps)));
    }
}
