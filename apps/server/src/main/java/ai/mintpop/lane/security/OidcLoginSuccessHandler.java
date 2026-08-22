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

import java.io.IOException;
import java.util.Optional;

/**
 * OIDC 登录成功处理：先建档/刷新资料（唯一建档入口），再按来源分叉——
 * 带桌面握手 Cookie 的渲染落地页（深链带一次性 ticket 回桌面端）；否则按管理端网页发会话 Cookie。
 * Logto 的 token 到这里就用完即弃：不下发、不落库。
 */
@Slf4j
@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserSyncService userSyncService;
    private final SessionTokenService sessionTokenService;
    private final TicketStore ticketStore;
    private final DesktopFlowCookie desktopFlowCookie;
    private final AuthProperties authProperties;
    private final DesktopReturnPage desktopReturnPage;

    public OidcLoginSuccessHandler(UserSyncService userSyncService,
                                   SessionTokenService sessionTokenService,
                                   TicketStore ticketStore,
                                   DesktopFlowCookie desktopFlowCookie,
                                   AuthProperties authProperties,
                                   DesktopReturnPage desktopReturnPage) {
        this.userSyncService = userSyncService;
        this.sessionTokenService = sessionTokenService;
        this.ticketStore = ticketStore;
        this.desktopFlowCookie = desktopFlowCookie;
        this.authProperties = authProperties;
        this.desktopReturnPage = desktopReturnPage;
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
            respondFailure(flow, response);
            return;
        }

        UserDto user = userSyncService.syncOnLogin(oidcUser.getSubject(), email, oidcUser.getFullName());

        if (flow.isPresent()) {
            // 桌面端：签一次性 ticket，渲染落地页把深链交回桌面端（不能裸 302 跳自定义
            // scheme——浏览器会静默拦截无用户手势的外部协议跳转，见 DesktopReturnPage）。
            // 会话 token 由 exchange 端点在验完 PKCE 后签发
            String ticket = ticketStore.create(flow.get().challenge(), user.getId());
            desktopReturnPage.renderSuccess(response, ticket, flow.get().state());
            return;
        }

        // 管理端网页：直接发会话 Cookie 回首页。相对路径回跳「当前请求所在的 host」——
        // 容器会按当前请求域名补全 Location，多域部署下谁发起登录就回谁，无需配置落点
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
        response.sendRedirect("/");
    }

    /**
     * 登录失败的落点：桌面流渲染落地页（深链带 error 回桌面端，让登录页停止空等），
     * 网页流 302 回当前域名首页带标记。public 供 SecurityConfig 的失败处理器复用。
     */
    public void respondFailure(Optional<DesktopFlowCookie.DesktopFlow> flow, HttpServletResponse response)
            throws IOException {
        if (flow.isPresent()) {
            desktopReturnPage.renderFailure(response, flow.get().state());
            return;
        }
        response.sendRedirect("/?login_error=1");
    }
}
