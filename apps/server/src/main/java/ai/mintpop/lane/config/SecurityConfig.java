package ai.mintpop.lane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 资源服务器安全配置。
 * 认证交给 Logto：本服务只验 JWT 的签名、issuer 与 audience（由
 * spring.security.oauth2.resourceserver.jwt.* 提供）。
 * 授权则完全来自本系统的 app_user.role，见 DbRoleJwtAuthenticationConverter。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, DbRoleJwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                // 无状态服务，客户端每次都带 JWT，不需要 CSRF 与会话
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // 引导配置匿名可取：客户端还没登录时就要用它去 Logto 授权
                        .requestMatchers(HttpMethod.GET, "/api/client-config").permitAll()
                        // 管理端要求库里的角色是 ADMIN，规则必须排在下面那条通配之前
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 未显式放行的路径一律拒绝，避免将来新增端点时忘记加规则
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }
}
