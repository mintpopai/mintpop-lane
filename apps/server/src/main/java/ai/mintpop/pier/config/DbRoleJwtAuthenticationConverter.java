package ai.mintpop.pier.config;

import ai.mintpop.pier.repository.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 把 JWT 转成带权限的认证对象。
 *
 * 权限来自本系统的 app_user.role，而不是 token 里的 scope / roles 声明——
 * 身份提供方只回答「你是谁」，「你能干什么」由业务系统自己说了算。
 * 库里查不到这个 sub 时不授予任何权限：他访问 /api/admin/** 会得到 403，
 * 访问 /api/link/** 仍按业务错误 ACCOUNT_NOT_ENROLLED 处理，行为不变。
 */
@Component
public class DbRoleJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public DbRoleJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = userRepository.findBySubject(jwt.getSubject())
                .<Collection<GrantedAuthority>>map(user ->
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .orElseGet(List::of);

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
