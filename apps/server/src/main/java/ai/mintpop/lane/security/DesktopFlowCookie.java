package ai.mintpop.lane.security;

import ai.mintpop.lane.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * 桌面端登录握手的中间态 Cookie：暂存 PKCE challenge 与桌面端 state，
 * 跨「/auth/desktop/start → Logto → /auth/callback」三跳存活，登录成功即读取并销毁。
 * 值可被浏览器持有者篡改，但篡改只会让本人的 PKCE 兑换失败，无提权面。
 */
@Component
public class DesktopFlowCookie {

    /** 够完成一次跳转登录即可 */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 桌面端握手中间态：PKCE challenge + 桌面端自己的防 CSRF state */
    public record DesktopFlow(String challenge, String state) {
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      String challenge, String state) {
        try {
            String value = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(new DesktopFlow(challenge, state)));
            response.addHeader(HttpHeaders.SET_COOKIE, build(request, value, TTL).toString());
        } catch (Exception e) {
            throw new IllegalStateException("桌面端握手中间态序列化失败", e);
        }
    }

    public Optional<DesktopFlow> read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, AuthProperties.DESKTOP_FLOW_COOKIE_NAME);
        if (cookie == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(
                    Base64.getUrlDecoder().decode(cookie.getValue()), DesktopFlow.class));
        } catch (Exception e) {
            // Cookie 损坏视为无中间态：按网页登录处理
            return Optional.empty();
        }
    }

    public void expire(HttpServletRequest request, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(request, "", Duration.ZERO).toString());
    }

    private ResponseCookie build(HttpServletRequest request, String value, Duration maxAge) {
        return ResponseCookie.from(AuthProperties.DESKTOP_FLOW_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }
}
