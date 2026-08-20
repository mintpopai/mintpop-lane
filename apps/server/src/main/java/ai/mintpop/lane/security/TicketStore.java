package ai.mintpop.lane.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 桌面端登录的一次性换取票据。内存表：单实例部署下足够，重启只影响正在登录的人。
 * 安全性质：ticket 为 32 字节高熵随机串、只存哈希、60 秒过期、兑换即销毁（含失败兑换）；
 * 兑换必须出示 PKCE verifier，与建票时的 challenge 比对——窃得 ticket 无 verifier 兑不出。
 */
@Component
public class TicketStore {

    private static final Duration TTL = Duration.ofSeconds(60);

    private record Entry(String codeChallenge, Long userId, Instant expiresAt) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    /** 建票：返回明文 ticket（放进深链），存储键是它的哈希 */
    public String create(String codeChallenge, Long userId) {
        cleanupExpired();
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        tickets.put(sha256(ticket), new Entry(codeChallenge, userId, Instant.now().plus(TTL)));
        return ticket;
    }

    /**
     * 兑换：一次性——无论成败先销票（失败兑换也销，防止拿同一张票离线穷举 verifier）。
     * 过期、不存在、S256(verifier) 与 challenge 不符，一律返回空。
     */
    public Optional<Long> redeem(String ticket, String verifier) {
        Entry entry = tickets.remove(sha256(ticket));
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        if (!sha256Base64Url(verifier).equals(entry.codeChallenge())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    /** 惰性清理：每次建票时顺手清掉过期条目，避免无人兑换的票堆积 */
    private void cleanupExpired() {
        Instant now = Instant.now();
        tickets.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    private static String sha256(String value) {
        return Base64.getEncoder().encodeToString(digest(value));
    }

    /** PKCE S256：BASE64URL 无填充编码，与 RFC 7636 一致 */
    private static String sha256Base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(value));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }
}
