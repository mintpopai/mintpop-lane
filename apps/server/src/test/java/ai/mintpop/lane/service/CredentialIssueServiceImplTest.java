package ai.mintpop.lane.service;

import ai.mintpop.lane.client.ClaudeOAuthClient;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
