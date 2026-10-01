package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.config.ClientVersionProperties;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.ClientVersionStatusResponse;
import ai.mintpop.lane.service.ClientVersionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 桌面端最新版本：查看服务端当前据以强制更新的版本，以及手动立即重拉一次清单
 * （发版后不想等下一轮定时拉取时用）。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN。
 */
@RestController
@RequestMapping("/api/admin/client-version")
public class AdminClientVersionController {

    private final ClientVersionService clientVersionService;
    private final ClientVersionProperties properties;

    public AdminClientVersionController(ClientVersionService clientVersionService,
                                        ClientVersionProperties properties) {
        this.clientVersionService = clientVersionService;
        this.properties = properties;
    }

    @GetMapping
    public ApiResponse<ClientVersionStatusResponse> get() {
        return ApiResponse.success(toResponse(clientVersionService.status()));
    }

    /** 拉取失败不报错：照常回状态，lastAttemptFailed=true，由管理端提示「沿用上一次的版本」 */
    @PostMapping("/refresh")
    public ApiResponse<ClientVersionStatusResponse> refresh() {
        clientVersionService.refresh();
        return ApiResponse.success(toResponse(clientVersionService.status()));
    }

    private ClientVersionStatusResponse toResponse(ClientVersionService.Status s) {
        return new ClientVersionStatusResponse(
                s.latest() == null ? null : s.latest().toString(),
                s.fetchedAt(),
                s.lastAttemptAt(),
                s.lastAttemptFailed(),
                properties.getManifestUrl());
    }
}
