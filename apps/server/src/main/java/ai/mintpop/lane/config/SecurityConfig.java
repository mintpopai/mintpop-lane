package ai.mintpop.lane.config;

import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.security.CookieOAuth2AuthorizationRequestRepository;
import ai.mintpop.lane.security.DesktopFlowCookie;
import ai.mintpop.lane.security.NoOpAuthorizedClientRepository;
import ai.mintpop.lane.security.OidcLoginSuccessHandler;
import ai.mintpop.lane.security.SessionAuthFilter;
import ai.mintpop.lane.service.SessionTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：日常鉴权只认自签会话 token（SessionAuthFilter，Bearer/Cookie 双载体）。
 * 认证只回答「这是库里哪个用户」；授权完全来自 app_user.role。
 * 会话 Cookie 为 SameSite=Lax：跨站 POST 不携带 Cookie，写接口天然免疫 CSRF，故禁用 CSRF 组件。
 */
@Slf4j
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionTokenService sessionTokenService,
                                                   UserRepository userRepository,
                                                   OidcLoginSuccessHandler successHandler,
                                                   DesktopFlowCookie desktopFlowCookie) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 登录握手与票据兑换发生在拿到会话之前，必须匿名可达
                        .requestMatchers("/auth/**", "/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/desktop/exchange").permitAll()
                        // 容器 ERROR dispatch（如 controller 内 response.sendError）会重新过一遍安全链，
                        // 不放行会被拦成 401，掩盖掉真实的 4xx/5xx 状态码
                        .requestMatchers("/error").permitAll()
                        // 管理端要求库里的角色是 ADMIN，规则必须排在下面那条通配之前
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 未显式放行的路径一律拒绝，避免将来新增端点时忘记加规则
                        .anyRequest().denyAll())
                .oauth2Login(login -> login
                        // 握手中间态存 Cookie（只对本浏览器有效），不落服务端 session
                        .authorizationEndpoint(ae -> ae.authorizationRequestRepository(
                                new CookieOAuth2AuthorizationRequestRepository()))
                        // Logto 的 access/refresh token 用完即弃：不让框架默认存进 HttpSession
                        // （否则会话即状态，且会顺带下发 JSESSIONID，与自签会话、后端无状态相悖）
                        .authorizedClientRepository(new NoOpAuthorizedClientRepository())
                        // 回调路径固定 /auth/callback，与 Logto 应用里注册的 Redirect URI 逐字一致
                        .redirectionEndpoint(re -> re.baseUri("/auth/callback"))
                        .successHandler(successHandler)
                        // 握手失败（用户取消/state 不符等）：桌面流深链带 error 回桌面端，网页流回管理端带标记
                        .failureHandler((request, response, exception) -> {
                            log.warn("OIDC 登录失败", exception);
                            var flow = desktopFlowCookie.read(request);
                            desktopFlowCookie.expire(request, response);
                            response.sendRedirect(successHandler.failureTarget(flow));
                        }))
                // 未登录访问受保护接口：返回 401（鉴权中间件允许用原生状态码），不重定向
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new SessionAuthFilter(sessionTokenService, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * ID Token 验签算法对齐 Logto：其 JWKS 与发现文档的
     * id_token_signing_alg_values_supported 均只有 ES384，而 Spring Security 默认只认 RS256，
     * 不显式指定会在回调时报 "Another algorithm expected"。Logto 轮换签名算法时需同步调整。
     */
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(registration -> SignatureAlgorithm.ES384);
        return factory;
    }
}
