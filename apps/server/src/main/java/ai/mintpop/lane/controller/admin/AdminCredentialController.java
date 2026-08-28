package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.request.CredentialExchangeRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.service.CredentialIssueService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 席位凭证签发与吊销。管理员在浏览器完成授权，服务端经该席位的落地出口兑换凭证；
 * 提前退订等场景下经同一出口发起吊销。
 * 整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN——这些接口能签出/吊销席位账号
 * 的真实凭证，必须锁死在管理端，见 AdminCredentialControllerTest 的鉴权用例。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCredentialController {

    private final CredentialIssueService credentialIssueService;

    public AdminCredentialController(CredentialIssueService credentialIssueService) {
        this.credentialIssueService = credentialIssueService;
    }

    @PostMapping("/subscriptions/{id}/credential/authorize-url")
    public ApiResponse<CredentialIssueService.AuthorizationStart> authorizeUrl(@PathVariable Long id) {
        return ApiResponse.success(credentialIssueService.startAuthorization(id));
    }

    // 返回体不含凭证本身：管理员不需要看到它，日志与前端也就不会留下副本。
    @PostMapping("/subscriptions/{id}/credential/exchange")
    public ApiResponse<CredentialIssueService.IssueResult> exchange(
            @PathVariable Long id, @Valid @RequestBody CredentialExchangeRequest request) {
        return ApiResponse.success(
                credentialIssueService.completeAuthorization(id, request.getSessionId(), request.getCode()));
    }

    // upstreamRevoked=false 不代表本次调用失败（业务码仍是成功），而是「本地已清理，
    // 上游可能仍然有效」——前端必须据此给出与吊销成功不同的提示，不能笼统当作一次成功操作。
    @PostMapping("/subscriptions/{id}/credential/revoke")
    public ApiResponse<CredentialIssueService.RevokeResult> revoke(@PathVariable Long id) {
        boolean upstreamRevoked = credentialIssueService.revokeCredential(id);
        return ApiResponse.success(new CredentialIssueService.RevokeResult(upstreamRevoked));
    }
}
