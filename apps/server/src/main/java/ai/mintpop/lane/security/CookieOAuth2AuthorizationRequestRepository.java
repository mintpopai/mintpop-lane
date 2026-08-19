package ai.mintpop.lane.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OIDC 握手中间态（state/nonce/PKCE verifier）存浏览器 Cookie，替代默认 HttpSession，
 * 保持后端完全无状态。中间态只对发起登录的浏览器自身有效，篡改只会让本人登录校验失败；
 * 用 JSON 序列化（不用 Java 序列化，规避反序列化攻击面）。
 */
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String COOKIE_NAME = "lane_oidc_flow";
    /** 中间态存活时间：够完成一次跳转登录即可 */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 序列化载体：只保留重建 OAuth2AuthorizationRequest 所需字段（取值均为字符串） */
    record FlowState(String authorizationUri, String clientId, String redirectUri,
                     Set<String> scopes, String state,
                     Map<String, String> additionalParameters,
                     Map<String, String> attributes) {
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        if (cookie == null) {
            return null;
        }
        try {
            FlowState s = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(cookie.getValue()), FlowState.class);
            return OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(s.authorizationUri())
                    .clientId(s.clientId())
                    .redirectUri(s.redirectUri())
                    .scopes(s.scopes())
                    .state(s.state())
                    .additionalParameters(Map.copyOf(s.additionalParameters()))
                    .attributes(attrs -> attrs.putAll(s.attributes()))
                    .build();
        } catch (Exception e) {
            // Cookie 损坏/格式不符：视为无中间态，让登录从头再来
            return null;
        }
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                         HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            expireCookie(request, response);
            return;
        }
        FlowState s = new FlowState(
                authorizationRequest.getAuthorizationUri(),
                authorizationRequest.getClientId(),
                authorizationRequest.getRedirectUri(),
                authorizationRequest.getScopes(),
                authorizationRequest.getState(),
                toStringMap(authorizationRequest.getAdditionalParameters()),
                toStringMap(authorizationRequest.getAttributes()));
        try {
            String value = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(s));
            response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, value, TTL).toString());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("OIDC 握手中间态序列化失败", e);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        expireCookie(request, response);
        return authorizationRequest;
    }

    private static Map<String, String> toStringMap(Map<String, Object> source) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(k, String.valueOf(v)));
        return result;
    }

    private void expireCookie(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(request, "", Duration.ZERO).toString());
    }

    private ResponseCookie buildCookie(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
