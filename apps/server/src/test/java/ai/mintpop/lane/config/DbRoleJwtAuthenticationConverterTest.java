package ai.mintpop.lane.config;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DbRoleJwtAuthenticationConverterTest {

    private static Jwt jwtOf(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                // 故意塞一个假的 scope：权限只能来自库，绝不能被 token 里的声明影响
                .claim("scope", "admin:manage")
                .build();
    }

    private static UserDto userWith(UserRole role) {
        UserDto u = new UserDto();
        u.setSubject("u1");
        u.setRole(role);
        return u;
    }

    @Test
    @DisplayName("库里是 ADMIN 就授予 ROLE_ADMIN")
    void 库里是管理员就授予管理员权限() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findBySubject("u1")).thenReturn(Optional.of(userWith(UserRole.ADMIN)));

        var auth = new DbRoleJwtAuthenticationConverter(repo).convert(jwtOf("u1"));

        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(auth.getName()).isEqualTo("u1");
    }

    @Test
    @DisplayName("库里是 MEMBER 就只有 ROLE_MEMBER")
    void 库里是普通成员就只有成员权限() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findBySubject("u1")).thenReturn(Optional.of(userWith(UserRole.MEMBER)));

        var auth = new DbRoleJwtAuthenticationConverter(repo).convert(jwtOf("u1"));

        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("token 里的 scope 声明不参与鉴权：库里没这个人就没有任何权限")
    void token里的scope不参与鉴权() {
        UserRepository repo = mock(UserRepository.class);
        when(repo.findBySubject(any())).thenReturn(Optional.empty());

        var auth = new DbRoleJwtAuthenticationConverter(repo).convert(jwtOf("陌生人"));

        assertThat(auth.getAuthorities()).isEmpty();
    }
}
