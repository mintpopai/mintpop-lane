package ai.mintpop.lane.service;

import ai.mintpop.lane.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 纯单元测试，不起 Spring 上下文：手工构造配置对象即可 */
class SessionTokenServiceTest {

    private static AuthProperties 配置(String secret) {
        AuthProperties properties = new AuthProperties();
        properties.setSessionSecret(secret);
        properties.setAdminFrontendUrl("http://admin.test.example");
        return properties;
    }

    private final SessionTokenService service =
            new SessionTokenService(配置("0123456789abcdef0123456789abcdef"));

    @Test
    @DisplayName("签发后能解析回同一个 userId")
    void 签发后能解析回同一个userId() {
        String token = service.issue(42L, Duration.ofMinutes(5));
        assertThat(service.parse(token)).contains(42L);
    }

    @Test
    @DisplayName("过期 token 解析为空")
    void 过期token解析为空() {
        String token = service.issue(42L, Duration.ofSeconds(-5));
        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("换一把密钥签的 token 解析为空（签名校验生效）")
    void 异钥token解析为空() {
        SessionTokenService other =
                new SessionTokenService(配置("fedcba9876543210fedcba9876543210"));
        String token = other.issue(42L, Duration.ofMinutes(5));
        assertThat(service.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("乱串解析为空而不抛异常")
    void 乱串解析为空() {
        assertThat(service.parse("不是token")).isEmpty();
    }

    @Test
    @DisplayName("密钥不足 32 字节时构造即抛，配置错误在启动期暴露")
    void 密钥过短启动即抛() {
        assertThatThrownBy(() -> new SessionTokenService(配置("太短")))
                .isInstanceOf(IllegalStateException.class);
    }
}
