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

    /**
     * 吊销该席位的凭证并清空本地记录。
     *
     * <p>吊销请求经该席位自己的落地出口发出，与签发同源；若落地节点已不可用（如用户
     * 已解绑落地节点），则跳过上游调用，仅清空本地。无论上游成败，本地凭证与全部
     * 元数据都会被清空——管理员点吊销的意图是「这个席位不该再有凭证」，本地不应
     * 继续留着它、继续下发给客户端；上游是否真的失效，由返回值与日志暴露。
     *
     * <p>⚠️「用 access_token 能否成功吊销」尚未用真实凭证验证过，见 {@link
     * ai.mintpop.lane.client.ClaudeOAuthClient#revoke}。返回 {@code false} 不代表
     * 操作失败，而是「本地已清理，上游可能仍然有效」，调用方必须把这一点透传给管理员。
     *
     * @param subscriptionId 目标订阅
     * @return 上游是否确认吊销成功
     */
    boolean revokeCredential(Long subscriptionId);

    /** 发起签发的结果 */
    record AuthorizationStart(String authUrl, String sessionId, String accountEmail, String egressIp) {
    }

    /** 兑换完成的结果 */
    record IssueResult(String accountEmail, String grantedScope, Instant expiresAt) {
    }

    /** 吊销的结果 */
    record RevokeResult(boolean upstreamRevoked) {
    }
}
