package ai.mintpop.lane.service;

import ai.mintpop.lane.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * 自签会话 token：HS256 JWT，claims 只含本系统 userid（sub）与起止时间。
 * 会话控制权归本系统：换密钥即全员下线；TTL 由调用方按载体（网页/桌面）决定。
 * 角色与处置态不进 token——每请求查库，保证停用/吊销即时生效。
 */
@Service
public class SessionTokenService {

    private final SecretKey key;

    public SessionTokenService(AuthProperties properties) {
        String secret = properties.getSessionSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("lane.auth.session-secret 未配置或不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发会话 token；subject 即本系统 app_user.id */
    public String issue(Long userId, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析会话 token：签名/过期/格式任一不符返回空（视为未登录，不抛异常） */
    public Optional<Long> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
