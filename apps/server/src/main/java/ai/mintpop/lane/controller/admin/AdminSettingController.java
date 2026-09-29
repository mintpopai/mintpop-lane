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

    /** 正在重算时拒绝改设置（改了也不会被这次重算用到，反而让人误以为生效了）；值变了才触发重算 */
    @PutMapping
    public ApiResponse<FrontSettingsResponse> update(@Valid @RequestBody FrontSettingsUpdateRequest request) {
        if (frontRebuildService.status().phase() == FrontRebuildPhase.RUNNING) {
            throw new BizException(BizCodeEnum.FRONT_REBUILD_RUNNING);
        }
        SystemSettingService.FrontSettingsChange change = systemSettingService.updateFrontSettings(request);
        renderCache.evictAll();
        if (change.changed()) {
            frontRebuildService.start();
        }
        return ApiResponse.success(toResponse(change.current()));
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
