package ai.mintpop.lane.security;

import ai.mintpop.lane.config.AuthProperties;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.service.UserSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * OIDC 登录成功处理：先建档/刷新资料（唯一建档入口），再按来源分叉——
 * 带桌面握手 Cookie 的发一次性 ticket 深链回桌面端；否则按管理端网页发会话 Cookie。
 * Logto 的 token 到这里就用完即弃：不下发、不落库。
 */
@Slf4j
@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    /** 桌面端深链回调地址，与桌面端 tauri-plugin-deep-link 注册的 scheme 逐字一致（反域名形态，见 RFC 8252） */
    static final String DESKTOP_CALLBACK = "ai.mintpop.lane://callback";

    private final UserSyncService userSyncService;
    private final SessionTokenService sessionTokenService;
    private final TicketStore ticketStore;
    private final DesktopFlowCookie desktopFlowCookie;
    private final AuthProperties authProperties;

    public OidcLoginSuccessHandler(UserSyncService userSyncService,
                                   SessionTokenService sessionTokenService,
                                   TicketStore ticketStore,
                                   DesktopFlowCookie desktopFlowCookie,
                                   AuthProperties authProperties) {
        this.userSyncService = userSyncService;
        this.sessionTokenService = sessionTokenService;
        this.ticketStore = ticketStore;
        this.desktopFlowCookie = desktopFlowCookie;
        this.authProperties = authProperties;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        Optional<DesktopFlowCookie.DesktopFlow> flow = desktopFlowCookie.read(request);
        desktopFlowCookie.expire(request, response);

        String email = oidcUser.getEmail();
        if (email == null || email.isBlank()) {
            // Logto 应用未勾选 email scope 时才会走到这：配置错误，不是用户的错
            log.error("id_token 缺少 email，检查 Logto 应用的 scope 配置");
            response.sendRedirect(failureTarget(flow));
            return;
        }

        UserDto user = userSyncService.syncOnLogin(oidcUser.getSubject(), email, oidcUser.getFullName());

        if (flow.isPresent()) {
            // 桌面端：签一次性 ticket 深链回去，会话 token 由 exchange 端点在验完 PKCE 后签发
            String ticket = ticketStore.create(flow.get().challenge(), user.getId());
            response.sendRedirect(DESKTOP_CALLBACK
                    + "?ticket=" + UriUtils.encodeQueryParam(ticket, StandardCharsets.UTF_8)
                    + "&state=" + UriUtils.encodeQueryParam(flow.get().state(), StandardCharsets.UTF_8));
            return;
        }

        // 管理端网页：直接发会话 Cookie 回首页
        ResponseCookie cookie = ResponseCookie.from(
                        AuthProperties.SESSION_COOKIE_NAME,
                        sessionTokenService.issue(user.getId(), authProperties.getWebSessionTtl()))
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(authProperties.getWebSessionTtl())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.sendRedirect(authProperties.getAdminFrontendUrl());
    }

    /** 登录失败的落点：桌面流深链带 error 回桌面端，网页流回管理端带标记。public 供 SecurityConfig 的失败处理器复用 */
    public String failureTarget(Optional<DesktopFlowCookie.DesktopFlow> flow) {
        return flow.map(f -> DESKTOP_CALLBACK + "?error=login_failed&state="
                        + UriUtils.encodeQueryParam(f.state(), StandardCharsets.UTF_8))
                .orElse(authProperties.getAdminFrontendUrl() + "?login_error=1");
    }
}
