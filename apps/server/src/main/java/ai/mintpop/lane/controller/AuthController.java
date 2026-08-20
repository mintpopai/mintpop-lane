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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 会话相关接口：桌面端票据兑换、当前用户视图、管理端登出。 */
@Slf4j
@RestController
public class AuthController {

    private final TicketStore ticketStore;
    private final SessionTokenService sessionTokenService;
    private final AuthProperties authProperties;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    public AuthController(TicketStore ticketStore,
                          SessionTokenService sessionTokenService,
                          AuthProperties authProperties,
                          UserRepository userRepository,
                          SubscriptionRepository subscriptionRepository) {
        this.ticketStore = ticketStore;
        this.sessionTokenService = sessionTokenService;
        this.authProperties = authProperties;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
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
        LocalDateTime now = LocalDateTime.now();
        List<MeResponse.MeSubscription> subscriptions =
                subscriptionRepository.findByUserId(userId).stream()
                        .map(s -> new MeResponse.MeSubscription(
                                s.getId(), s.getName(), s.getAgentType(),
                                s.getStartsAt(), s.getEndsAt(), s.isActiveAt(now)))
                        .toList();
        return ApiResponse.success(new MeResponse(
                user.getId(), user.getEmail(), user.getName(), user.getRole(), subscriptions));
    }

    /** 管理端网页登出：清会话 Cookie 后回前端。桌面端登出=删钥匙串，不经过服务端 */
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
        response.sendRedirect(authProperties.getAdminFrontendUrl());
    }
}
