package ai.mintpop.lane.controller;

import ai.mintpop.lane.config.AuthProperties;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.DesktopExchangeRequest;
import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.DesktopSessionResponse;
import ai.mintpop.lane.response.MeResponse;
import ai.mintpop.lane.security.TicketStore;
import ai.mintpop.lane.service.SessionTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 会话相关接口：桌面端票据兑换、当前用户视图、管理端登出。 */
@Slf4j
@RestController
public class AuthController {

    /** OIDC 客户端注册 id，与 application.yaml 里 spring.security.oauth2.client.registration 下的键一致 */
    private static final String LOGTO_REGISTRATION_ID = "logto";

    private final TicketStore ticketStore;
    private final SessionTokenService sessionTokenService;
    private final AuthProperties authProperties;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final Clock clock;

    public AuthController(TicketStore ticketStore,
                          SessionTokenService sessionTokenService,
                          AuthProperties authProperties,
                          UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository,
                          ClientRegistrationRepository clientRegistrationRepository,
                          Clock clock) {
        this.ticketStore = ticketStore;
        this.sessionTokenService = sessionTokenService;
        this.authProperties = authProperties;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.clock = clock;
    }

    /** 桌面端用一次性 ticket + PKCE verifier 兑换长效会话 token */
    @PostMapping("/api/auth/desktop/exchange")
    public ApiResponse<DesktopSessionResponse> exchange(@Valid @RequestBody DesktopExchangeRequest request) {
        Long userId = ticketStore.redeem(request.getTicket(), request.getVerifier())
                .orElseThrow(() -> {
                    // 兑换失败值得留痕：同票二兑是有人抢票的信号
                    log.warn("桌面端票据兑换失败");
                    return new BizException(BizCodeEnum.TICKET_INVALID);
                });
        Duration ttl = authProperties.getDesktopSessionTtl();
        return ApiResponse.success(
                new DesktopSessionResponse(sessionTokenService.issue(userId, ttl), ttl.toSeconds()));
    }

    /** 当前用户信息与订阅概览。也充当桌面端启动时的会话验活端点 */
    @GetMapping("/api/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Long userId) {
        // filter 已确认用户存在；两次查询间被删的窗口极小，按内部错误兜底即可
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(BizCodeEnum.INTERNAL_ERROR));
        Instant now = clock.instant();
        List<MeResponse.MeSubscription> subscriptions =
                subscriptionRepository.findByUserId(userId).stream()
                        .map(s -> new MeResponse.MeSubscription(
                                s.getId(), s.getName(), s.getAgentType(),
                                s.getStartsAt(), s.getEndsAt(), s.isActiveAt(now)))
                        .toList();
        return ApiResponse.success(new MeResponse(
                user.getId(), user.getEmail(), user.getRole(), subscriptions));
    }

    /**
     * 管理端网页登出：先清本站会话 Cookie，再尽量把 Logto 那边的 IdP 会话也结束掉。
     * 只清 Cookie 是不够的——Logto 侧仍留着登录态，用户下次进受保护页会被静默重登，
     * 「退出」就成了错觉，所以这里走 OIDC 的 RP-initiated logout。
     * 桌面端登出=删钥匙串，不经过服务端；IdP 侧残留的会话由桌面流授权请求的
     * prompt=login 兜底（见 DesktopAwareAuthorizationRequestResolver），重登必须重输凭据。
     */
    @GetMapping("/auth/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ResponseCookie expired = ResponseCookie.from(AuthProperties.SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expired.toString());
        response.sendRedirect(logoutRedirectUrl(request));
    }

    /** 登出回调：Logto 清完 IdP 会话回到这里，再回当前域名的前端首页 */
    @GetMapping("/auth/logout/callback")
    public void logoutCallback(HttpServletResponse response) throws IOException {
        response.sendRedirect("/");
    }

    /**
     * 登出后该跳去哪，两条路径：
     * 1. issuer-uri 发现模式下，Spring 会把发现文档整份放进 configurationMetadata，
     *    其中的 end_session_endpoint 就是 Logto 的结束会话端点——跳它并带上 client_id 与
     *    post_logout_redirect_uri（指向本服务的 /auth/logout/callback，按当前请求域名动态拼出，
     *    多域部署下谁发起登出就回谁；各域名的该地址都须在 Logto 应用里登记），IdP 会话才真正结束；
     * 2. 显式端点配置（如测试环境）拿不到 metadata，没有可用的结束会话端点，
     *    只能回退成「只清本站 Cookie 后回当前域名首页」——本站已登出，Logto 侧留待其自然过期。
     */
    private String logoutRedirectUrl(HttpServletRequest request) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(LOGTO_REGISTRATION_ID);
        if (registration == null) {
            return "/";
        }
        Object endpoint = registration.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
        if (endpoint == null || endpoint.toString().isBlank()) {
            return "/";
        }
        String postLogoutRedirect = ServletUriComponentsBuilder.fromContextPath(request)
                .path("/auth/logout/callback").build().toUriString();
        // 端点自身可能已带查询串，拼接符按需选择；两个参数值都做 URL 编码后再拼
        String separator = endpoint.toString().contains("?") ? "&" : "?";
        return endpoint + separator
                + "client_id=" + URLEncoder.encode(registration.getClientId(), StandardCharsets.UTF_8)
                + "&post_logout_redirect_uri=" + URLEncoder.encode(postLogoutRedirect, StandardCharsets.UTF_8);
    }
}
