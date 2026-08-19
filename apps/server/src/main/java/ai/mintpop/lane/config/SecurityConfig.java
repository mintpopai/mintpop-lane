package ai.mintpop.lane.config;

import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.security.SessionAuthFilter;
import ai.mintpop.lane.service.SessionTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置：日常鉴权只认自签会话 token（SessionAuthFilter，Bearer/Cookie 双载体）。
 * 认证只回答「这是库里哪个用户」；授权完全来自 app_user.role。
 * 会话 Cookie 为 SameSite=Lax：跨站 POST 不携带 Cookie，写接口天然免疫 CSRF，故禁用 CSRF 组件。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SessionTokenService sessionTokenService,
                                                   UserRepository userRepository) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 登录握手与票据兑换发生在拿到会话之前，必须匿名可达
                        .requestMatchers("/auth/**", "/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/desktop/exchange").permitAll()
                        // 管理端要求库里的角色是 ADMIN，规则必须排在下面那条通配之前
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 未显式放行的路径一律拒绝，避免将来新增端点时忘记加规则
                        .anyRequest().denyAll())
                // 未登录访问受保护接口：返回 401（鉴权中间件允许用原生状态码），不重定向
                .exceptionHandling(eh -> eh.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new SessionAuthFilter(sessionTokenService, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
