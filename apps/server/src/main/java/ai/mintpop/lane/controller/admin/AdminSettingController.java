package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.FrontRebuildPhase;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.FrontSettingsResponse;
import ai.mintpop.lane.service.FrontRebuildService;
import ai.mintpop.lane.service.SubscriptionRenderCache;
import ai.mintpop.lane.service.SystemSettingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/** 全局配置。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。 */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingController {

    private final SystemSettingService systemSettingService;
    private final SubscriptionRenderCache renderCache;
    private final FrontRebuildService frontRebuildService;

    public AdminSettingController(SystemSettingService systemSettingService, SubscriptionRenderCache renderCache,
                                  FrontRebuildService frontRebuildService) {
        this.systemSettingService = systemSettingService;
        this.renderCache = renderCache;
        this.frontRebuildService = frontRebuildService;
    }

    @GetMapping
    public ApiResponse<FrontSettingsResponse> get() {
        return ApiResponse.success(toResponse(systemSettingService.frontSettings()));
    }

    /**
     * 只保存配置，不触发全体重算：存量用户的线路只在管理员手动点「重算全部线路」时整体重排，
     * 新值在那之前只影响之后的单人分配。正在重算时拒绝改设置，免得这次重算读到一半新一半旧的值
     */
    @PutMapping
    public ApiResponse<FrontSettingsResponse> update(@Valid @RequestBody FrontSettingsUpdateRequest request) {
        if (frontRebuildService.status().phase() == FrontRebuildPhase.RUNNING) {
            throw new BizException(BizCodeEnum.FRONT_REBUILD_RUNNING);
        }
        FrontSettings current = systemSettingService.updateFrontSettings(request);
        renderCache.evictAll();
        return ApiResponse.success(toResponse(current));
    }

    static FrontSettingsResponse toResponse(FrontSettings s) {
        return new FrontSettingsResponse(
                s.region(),
                Arrays.stream(NodeRegion.values())
                        .map(r -> new FrontSettingsResponse.RegionOption(r, r.getDisplayName()))
                        .toList(),
                s.airportsPerUser(),
                s.bandwidthPerUserMbps());
    }
}
