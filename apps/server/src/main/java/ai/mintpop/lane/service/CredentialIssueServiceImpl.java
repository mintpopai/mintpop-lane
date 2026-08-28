package ai.mintpop.lane.service;

import ai.mintpop.lane.client.ClaudeOAuthClient;
import ai.mintpop.lane.config.ClaudeOAuthProperties;
import ai.mintpop.lane.crypto.CredentialCipher;
import ai.mintpop.lane.crypto.PkceGenerator;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.OAuthSession;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.OAuthSessionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 席位凭证签发编排：管理员发起签发 → 生成授权链接 → 管理员在浏览器授权后
 * 把 code 贴回 → 服务端经该席位的落地出口兑换凭证并加密落库。
 */
@Slf4j
@Service
public class CredentialIssueServiceImpl implements CredentialIssueService {

    /** 会话有效期：管理员在浏览器完成授权再贴回 code，半小时足够 */
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    /** 有效期校验下限：低于请求值的这个比例即视为被截断 */
    private static final double LIFETIME_TOLERANCE = 0.82;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final OAuthSessionRepository sessionRepository;
    private final CredentialIssueGuard guard;
    private final CredentialLifetimeCalculator lifetimeCalculator;
    private final ClaudeOAuthClient oauthClient;
    private final ClaudeOAuthProperties properties;
    private final PkceGenerator pkce;
    private final CredentialCipher cipher;
    private final Clock clock;

    public CredentialIssueServiceImpl(SubscriptionRepository subscriptionRepository,
                                      UserRepository userRepository,
                                      ProxyNodeRepository nodeRepository,
                                      OAuthSessionRepository sessionRepository,
                                      CredentialIssueGuard guard,
                                      CredentialLifetimeCalculator lifetimeCalculator,
                                      ClaudeOAuthClient oauthClient,
                                      ClaudeOAuthProperties properties,
                                      PkceGenerator pkce,
                                      CredentialCipher cipher,
                                      Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.sessionRepository = sessionRepository;
        this.guard = guard;
        this.lifetimeCalculator = lifetimeCalculator;
        this.oauthClient = oauthClient;
        this.properties = properties;
        this.pkce = pkce;
        this.cipher = cipher;
        this.clock = clock;
    }

    @Override
    public AuthorizationStart startAuthorization(Long subscriptionId) {
        SubscriptionDto subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));
        UserDto user = userRepository.findById(subscription.getUserId())
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        ProxyNodeDto front = user.getFrontNodeId() == null ? null
                : nodeRepository.findById(user.getFrontNodeId()).orElse(null);
        ProxyNodeDto land = user.getLandNodeId() == null ? null
                : nodeRepository.findById(user.getLandNodeId()).orElse(null);

        guard.check(subscription, user, front, land);

        String verifier = pkce.newVerifier();
        String state = pkce.newState();
        String sessionId = pkce.newSessionId();

        OAuthSession session = new OAuthSession();
        session.setSessionId(sessionId);
        session.setSubscriptionId(subscriptionId);
        session.setCodeVerifierCipher(cipher.encrypt(verifier));
        session.setState(state);
        session.setScope(properties.getScope());
        session.setExpiresAt(clock.instant().plus(SESSION_TTL));
        sessionRepository.save(session);

        // 返回账号邮箱与出口 IP 供管理员在授权前核对：
        // 浏览器里登录错账号是这个流程最容易犯的错
        return new AuthorizationStart(
                oauthClient.buildAuthorizeUrl(state, pkce.challengeOf(verifier), properties.getScope()),
                sessionId,
                subscription.getAccountEmail(),
                land.getEgressIp());
    }

    @Override
    @Transactional
    public IssueResult completeAuthorization(Long subscriptionId, String sessionId, String code) {
        OAuthSession session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BizException(BizCodeEnum.OAUTH_SESSION_INVALID));
        if (!session.getSubscriptionId().equals(subscriptionId)
                || session.getExpiresAt().isBefore(clock.instant())) {
            throw new BizException(BizCodeEnum.OAUTH_SESSION_INVALID);
        }

        SubscriptionDto subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));
        UserDto user = userRepository.findById(subscription.getUserId())
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        ProxyNodeDto land = nodeRepository.findById(user.getLandNodeId())
                .orElseThrow(() -> new BizException(BizCodeEnum.LINK_NOT_READY_FOR_ISSUE));

        // 回调页给出的形如 authCode#state，与 CLI 的拆分规则一致
        int hash = code.indexOf('#');
        String authCode = hash < 0 ? code : code.substring(0, hash);
        String codeState = hash < 0 ? session.getState() : code.substring(hash + 1);
        // 管理员可能把 A 席位授权页贴回的 code 粘进 B 席位的表单；PKCE 已挡住可利用的攻击变体
        // （伪造 code 配不上本会话的 verifier），这里校验 state 只为把「粘错窗口」变成明确报错
        if (!codeState.equals(session.getState())) {
            throw new BizException(BizCodeEnum.OAUTH_SESSION_INVALID);
        }

        long requested = lifetimeCalculator.secondsFor(subscription.getEndsAt());
        ClaudeOAuthClient.TokenResult result = oauthClient.exchange(land,
                new ClaudeOAuthClient.ExchangeCommand(
                        authCode, codeState, cipher.decrypt(session.getCodeVerifierCipher()), requested));

        validateTokenResult(result, requested);

        Instant expiresAt = result.issuedAt().plusSeconds(result.expiresIn());
        subscriptionRepository.updateCredential(subscriptionId,
                cipher.encrypt(result.accessToken()),
                result.scope(),
                result.tokenUuid(),
                result.issuedAt(),
                expiresAt,
                result.refreshToken().isEmpty() ? null : cipher.encrypt(result.refreshToken()));
        sessionRepository.deleteBySessionId(sessionId);

        return new IssueResult(subscription.getAccountEmail(), result.scope(), expiresAt);
    }

    @Override
    @Transactional
    public boolean revokeCredential(Long subscriptionId) {
        SubscriptionDto subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));
        if (subscription.getCredential() == null || subscription.getCredential().isBlank()) {
            throw new BizException(BizCodeEnum.CREDENTIAL_NOT_FOUND);
        }

        ProxyNodeDto land = findLandNode(subscription.getUserId());
        boolean upstreamRevoked = false;
        if (land == null) {
            // 链路可能已被拆除（用户解绑落地节点），此时无法经该出口发起吊销；
            // 管理员的清理意图仍应被执行，只跳过上游调用、不抛异常
            log.warn("吊销凭证跳过上游调用：落地节点不可用，本地已清理，上游凭证可能仍然有效：subscriptionId={}",
                    subscriptionId);
        } else {
            // DTO 里的 credential 已被 SubscriptionConverter.toDto 解密成明文，这里直接用；
            // 再解密一次会在 Base64 解码处炸掉（真实 token 含 '-'）
            upstreamRevoked = oauthClient.revoke(land, subscription.getCredential());
            if (upstreamRevoked) {
                log.info("吊销凭证成功：subscriptionId={}", subscriptionId);
            } else {
                // ⚠️ access_token 吊销尚未实测有效，见 ClaudeOAuthClient.revoke；
                // 失败不代表本地保留凭证是对的，仍按管理员意图清空，只是不能静默假装吊销成功
                log.warn("吊销凭证失败：本地已清理，上游可能仍然有效：subscriptionId={}", subscriptionId);
            }
        }

        subscriptionRepository.clearCredential(subscriptionId);
        return upstreamRevoked;
    }

    /** 取该用户当前绑定的落地节点；用户不存在、未绑定落地节点、节点已被删除都统一按「不可用」处理 */
    private ProxyNodeDto findLandNode(Long userId) {
        return userRepository.findById(userId)
                .map(UserDto::getLandNodeId)
                .flatMap(nodeRepository::findById)
                .orElse(null);
    }

    /**
     * 校验服务端实际给了什么。上游随时可能收紧策略，
     * 静默接受一个残缺凭证比签发失败更糟 —— 前者要等用户报「Fable 又不见了」才会发现。
     */
    static void validateTokenResult(ClaudeOAuthClient.TokenResult result, long requestedSeconds) {
        if (!result.scope().contains("user:profile")) {
            log.warn("上游签发的凭证 scope 不足，成为孤儿凭证（不落库不吊销）：tokenUuid={} scope={} expiresIn={}",
                    result.tokenUuid(), result.scope(), result.expiresIn());
            throw new BizException(BizCodeEnum.CREDENTIAL_SCOPE_INSUFFICIENT);
        }
        if (result.expiresIn() < requestedSeconds * LIFETIME_TOLERANCE) {
            log.warn("上游签发的凭证有效期被截断，成为孤儿凭证（不落库不吊销）：tokenUuid={} scope={} expiresIn={}",
                    result.tokenUuid(), result.scope(), result.expiresIn());
            throw new BizException(BizCodeEnum.CREDENTIAL_LIFETIME_TRUNCATED);
        }
    }
}
