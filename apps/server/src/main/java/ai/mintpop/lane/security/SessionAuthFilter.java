package ai.mintpop.lane.security;

import ai.mintpop.lane.config.AuthProperties;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.util.List;

/**
 * 会话认证过滤器：解析自签会话 token（桌面端 Bearer 头 / 管理端 Cookie 双载体），
 * 验签得 userid 后查库装配角色。角色与处置态每请求查库——停用/吊销改一行库下一个请求即生效。
 * 用户已被删除时不装配认证（等同未登录 401），客户端据此回登录页。
 * 处置态 SUSPENDED/REVOKED 不在这里拦：这类用户仍需能调心跳得知自己被停用，拦截由业务层做。
 * 无效/缺失静默放行（是否放行由授权规则裁决）。
 * 注意：不加 @Component——由 SecurityConfig 显式装入安全链，避免被 Servlet 容器再注册一次。
 */
public class SessionAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionTokenService sessionTokenService;
    private final UserRepository userRepository;

    public SessionAuthFilter(SessionTokenService sessionTokenService, UserRepository userRepository) {
        this.sessionTokenService = sessionTokenService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            sessionTokenService.parse(token).flatMap(userRepository::findById).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    user.getId(), null,
                                    List.of(new SimpleGrantedAuthority(authorityOf(user))))));
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 被处置用户仍需通过认证以便心跳告知其处置态，但不再享有任何角色权限：
     * status 非 ACTIVE 时一律装配 ROLE_RESTRICTED（不带任何业务角色），而非其库里的原始角色，
     * 否则停用/吊销的 ADMIN 会因为角色字段未变而继续保有全部管理端权限。
     */
    private String authorityOf(UserDto user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            return "ROLE_RESTRICTED";
        }
        return "ROLE_" + user.getRole().name();
    }

    /** Bearer 头优先（桌面端），其次会话 Cookie（管理端网页） */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        Cookie cookie = WebUtils.getCookie(request, AuthProperties.SESSION_COOKIE_NAME);
        return cookie == null ? null : cookie.getValue();
    }
}
