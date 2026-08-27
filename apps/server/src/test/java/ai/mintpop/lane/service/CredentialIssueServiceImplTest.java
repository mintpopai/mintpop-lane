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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialIssueServiceImplTest {

    @Test
    @DisplayName("服务端授予的 scope 缺 user:profile 时拒绝落库：静默接受残缺凭证比失败更糟")
    void rejectsWhenProfileScopeMissing() {
        ClaudeOAuthClient.TokenResult result = new ClaudeOAuthClient.TokenResult(
                "sk-ant-oat01-x", "sk-ant-ort01-x", "user:inference",
                31536000L, Instant.parse("2026-08-27T00:00:00Z"), "uuid-1");

        assertThatThrownBy(() -> CredentialIssueServiceImpl.validateTokenResult(result, 31536000L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_SCOPE_INSUFFICIENT);
    }

    @Test
    @DisplayName("有效期被服务端大幅压缩时拒绝落库：说明上游策略变了，要当场发现")
    void rejectsWhenLifetimeTruncated() {
        ClaudeOAuthClient.TokenResult result = new ClaudeOAuthClient.TokenResult(
                "sk-ant-oat01-x", "sk-ant-ort01-x", "user:inference user:profile",
                28800L, Instant.parse("2026-08-27T00:00:00Z"), "uuid-1");

        assertThatThrownBy(() -> CredentialIssueServiceImpl.validateTokenResult(result, 31536000L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_LIFETIME_TRUNCATED);
    }

    @Test
    @DisplayName("回调 state 与会话记录不符时拒绝兑换：管理员粘错窗口（A 席位的 code 贴进 B 席位）要得到明确报错，而不是语焉不详的上游 400")
    void rejectsWhenCallbackStateMismatchesSession() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        ProxyNodeRepository nodeRepository = mock(ProxyNodeRepository.class);
        OAuthSessionRepository sessionRepository = mock(OAuthSessionRepository.class);
        CredentialIssueGuard guard = mock(CredentialIssueGuard.class);
        CredentialLifetimeCalculator lifetimeCalculator = mock(CredentialLifetimeCalculator.class);
        ClaudeOAuthClient oauthClient = mock(ClaudeOAuthClient.class);
        ClaudeOAuthProperties properties = mock(ClaudeOAuthProperties.class);
        PkceGenerator pkce = mock(PkceGenerator.class);
        CredentialCipher cipher = mock(CredentialCipher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T00:00:00Z"), ZoneOffset.UTC);

        Long subscriptionId = 1L;
        OAuthSession session = new OAuthSession();
        session.setSessionId("sess-1");
        session.setSubscriptionId(subscriptionId);
        session.setCodeVerifierCipher("cipher-verifier");
        session.setState("expected-state");
        session.setScope("user:inference user:profile");
        session.setExpiresAt(clock.instant().plusSeconds(600));
        when(sessionRepository.findBySessionId("sess-1")).thenReturn(Optional.of(session));

        SubscriptionDto subscription = new SubscriptionDto();
        subscription.setId(subscriptionId);
        subscription.setUserId(2L);
        subscription.setEndsAt(clock.instant().plusSeconds(86400));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(subscription));

        UserDto user = new UserDto();
        user.setId(2L);
        user.setLandNodeId(3L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        ProxyNodeDto land = new ProxyNodeDto();
        land.setId(3L);
        when(nodeRepository.findById(3L)).thenReturn(Optional.of(land));

        CredentialIssueServiceImpl service = new CredentialIssueServiceImpl(
                subscriptionRepository, userRepository, nodeRepository, sessionRepository,
                guard, lifetimeCalculator, oauthClient, properties, pkce, cipher, clock);

        assertThatThrownBy(() -> service.completeAuthorization(subscriptionId, "sess-1", "auth-code#wrong-state"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.OAUTH_SESSION_INVALID);

        verify(oauthClient, never()).exchange(any(), any());
    }
}
