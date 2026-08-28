package ai.mintpop.lane.service;

import java.time.Instant;

/**
 * 席位凭证签发编排：管理员发起签发 → 生成授权链接 → 管理员在浏览器授权后
 * 把 code 贴回 → 服务端经该席位的落地出口兑换凭证并加密落库。
 */
public interface CredentialIssueService {

    /**
     * 发起签发，生成授权链接与一次性会话。
     *
     * @param subscriptionId 目标订阅
     * @return 授权链接与核对信息（账号邮箱、出口 IP 供管理员在授权前核对，
     *         登录错账号是这个流程最容易犯的错）
     */
    AuthorizationStart startAuthorization(Long subscriptionId);

    /**
     * 用管理员贴回的授权码兑换凭证并加密落库。
     *
     * @param subscriptionId 目标订阅，须与发起签发时一致
     * @param sessionId      发起签发时返回的会话标识
     * @param code           回调页给出的授权码，形如 {@code authCode#state}
     */
    IssueResult completeAuthorization(Long subscriptionId, String sessionId, String code);

    /** 发起签发的结果 */
    record AuthorizationStart(String authUrl, String sessionId, String accountEmail, String egressIp) {
    }

    /** 兑换完成的结果 */
    record IssueResult(String accountEmail, String grantedScope, Instant expiresAt) {
    }
}
