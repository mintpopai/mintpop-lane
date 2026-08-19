package ai.mintpop.lane.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯单元测试，不起 Spring 上下文：验证「写 Cookie → 搬进新请求读回」的往返保真，
 * 以及 Cookie 损坏时的静默降级（不抛异常，视为无中间态）。
 */
class CookieOAuth2AuthorizationRequestRepositoryTest {

    private final CookieOAuth2AuthorizationRequestRepository repository =
            new CookieOAuth2AuthorizationRequestRepository();

    private static OAuth2AuthorizationRequest sampleRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("http://127.0.0.1:9/oidc/auth")
                .clientId("test-client-id")
                .redirectUri("http://localhost:8080/auth/callback")
                .scopes(Set.of("openid", "profile", "email"))
                .state("state-0123456789")
                .additionalParameters(Map.of("code_challenge", "abc-challenge",
                        "code_challenge_method", "S256"))
                .attributes(attrs -> attrs.putAll(Map.of(
                        "nonce", "nonce-0123456789",
                        "code_verifier", "verifier-0123456789")))
                .build();
    }

    @Test
    @DisplayName("写出 Cookie 后搬进新请求读回：state/attributes/scopes/authorizationUri/clientId/redirectUri 往返保真")
    void 写读往返保真() {
        OAuth2AuthorizationRequest original = sampleRequest();

        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

        String cookieValue = saveResponse.getHeader("Set-Cookie")
                .split(";")[0].split("=", 2)[1];
        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(new jakarta.servlet.http.Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo(original.getState());
        assertThat(loaded.getAuthorizationUri()).isEqualTo(original.getAuthorizationUri());
        assertThat(loaded.getClientId()).isEqualTo(original.getClientId());
        assertThat(loaded.getRedirectUri()).isEqualTo(original.getRedirectUri());
        assertThat(loaded.getScopes()).isEqualTo(original.getScopes());
        assertThat(loaded.getAttributes())
                .containsEntry("nonce", "nonce-0123456789")
                .containsEntry("code_verifier", "verifier-0123456789");
    }

    @Test
    @DisplayName("removeAuthorizationRequest 读回原值并使 Cookie 过期")
    void 移除时读回原值并过期Cookie() {
        OAuth2AuthorizationRequest original = sampleRequest();

        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, saveRequest, saveResponse);
        String cookieValue = saveResponse.getHeader("Set-Cookie")
                .split(";")[0].split("=", 2)[1];

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setCookies(new jakarta.servlet.http.Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, cookieValue));
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed =
                repository.removeAuthorizationRequest(removeRequest, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo(original.getState());
        assertThat(removeResponse.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    @DisplayName("Cookie 损坏时 loadAuthorizationRequest 返回 null 而不抛异常")
    void cookie损坏返回null不抛异常() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie(
                CookieOAuth2AuthorizationRequestRepository.COOKIE_NAME, "not-a-valid-base64url-json!!"));

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    @DisplayName("无 Cookie 时 loadAuthorizationRequest 返回 null")
    void 无cookie返回null() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }
}
