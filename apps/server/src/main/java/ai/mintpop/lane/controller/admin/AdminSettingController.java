package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.request.FrontSettingsUpdateRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.FrontSettingsResponse;
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

    public AdminSettingController(SystemSettingService systemSettingService, SubscriptionRenderCache renderCache) {
        this.systemSettingService = systemSettingService;
        this.renderCache = renderCache;
    }

    @GetMapping
    public ApiResponse<FrontSettingsResponse> get() {
        return ApiResponse.success(toResponse(systemSettingService.frontSettings()));
    }

    /** 保存后清空订阅渲染缓存（地区会影响渲染）。触发全体重算在 Task 7 接上 */
    @PutMapping
    public ApiResponse<FrontSettingsResponse> update(@Valid @RequestBody FrontSettingsUpdateRequest request) {
        SystemSettingService.FrontSettingsChange change = systemSettingService.updateFrontSettings(request);
        renderCache.evictAll();
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
