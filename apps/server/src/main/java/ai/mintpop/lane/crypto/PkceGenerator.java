package ai.mintpop.lane.crypto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** PKCE（RFC 7636）取值生成。全部为 base64url 无填充形式，与 Claude Code CLI 一致。 */
@Component
public class PkceGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** code_verifier：32 随机字节 → base64url，长度 43 */
    public String newVerifier() {
        return URL_ENCODER.encodeToString(randomBytes(32));
    }

    /** code_challenge：verifier 的 SHA-256 → base64url（S256 方法） */
    public String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }

    /** OAuth state：32 随机字节 → base64url */
    public String newState() {
        return URL_ENCODER.encodeToString(randomBytes(32));
    }

    /** 会话标识：16 随机字节的十六进制 */
    public String newSessionId() {
        return HexFormat.of().formatHex(randomBytes(16));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
