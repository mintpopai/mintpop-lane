package ai.mintpop.lane.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** 纯单元测试。PKCE 关系用真实的 S256 计算构造。 */
class TicketStoreTest {

    private final TicketStore store = new TicketStore();

    /** 与桌面端一致的 S256：challenge = BASE64URL_无填充(SHA256(verifier)) */
    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    @Test
    @DisplayName("正确的 verifier 能兑换出 userId")
    void 正确verifier兑换成功() throws Exception {
        String verifier = "correct-verifier-with-enough-entropy-0123456789";
        String ticket = store.create(s256(verifier), 42L);
        assertThat(store.redeem(ticket, verifier)).contains(42L);
    }

    @Test
    @DisplayName("verifier 不匹配兑换失败（窃得 ticket 无 verifier 兑不出）")
    void verifier不匹配兑换失败() throws Exception {
        String ticket = store.create(s256("real-verifier-0123456789-0123456789-01"), 42L);
        assertThat(store.redeem(ticket, "attacker-guess-verifier-0123456789-012")).isEmpty();
    }

    @Test
    @DisplayName("ticket 一次性：兑换成功后同票再兑失败")
    void ticket一次性() throws Exception {
        String verifier = "correct-verifier-with-enough-entropy-0123456789";
        String ticket = store.create(s256(verifier), 42L);
        assertThat(store.redeem(ticket, verifier)).contains(42L);
        assertThat(store.redeem(ticket, verifier)).isEmpty();
    }

    @Test
    @DisplayName("不存在的 ticket 兑换失败")
    void 不存在的ticket兑换失败() {
        assertThat(store.redeem("no-such-ticket", "whatever-verifier")).isEmpty();
    }

    @Test
    @DisplayName("verifier 不匹配的失败兑换也销票——防离线穷举")
    void 失败兑换也销票() throws Exception {
        String verifier = "correct-verifier-with-enough-entropy-0123456789";
        String ticket = store.create(s256(verifier), 42L);
        store.redeem(ticket, "wrong-verifier-0123456789-0123456789-012");
        assertThat(store.redeem(ticket, verifier)).isEmpty();
    }
}
