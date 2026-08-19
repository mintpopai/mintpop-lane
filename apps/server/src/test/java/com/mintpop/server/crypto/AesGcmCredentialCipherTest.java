package com.mintpop.server.crypto;

import com.mintpop.server.config.CryptoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCredentialCipherTest {

    private static AesGcmCredentialCipher cipher() {
        CryptoProperties props = new CryptoProperties();
        props.setKey(Base64.getEncoder().encodeToString("mintpop-test-key-0123456789abcd!".getBytes()));
        return new AesGcmCredentialCipher(props);
    }

    @Test
    @DisplayName("加密后能原样解回")
    void 加密后能原样解回() {
        var c = cipher();
        String plain = "sk-ant-oat01-很长的席位凭据";

        assertThat(c.decrypt(c.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    @DisplayName("同一明文两次加密得到不同密文（随机 IV）")
    void 同一明文两次加密得到不同密文() {
        var c = cipher();

        assertThat(c.encrypt("同一个值")).isNotEqualTo(c.encrypt("同一个值"));
    }

    @Test
    @DisplayName("密文被篡改后解密失败，绝不返回可疑明文")
    void 密文被篡改后解密失败() {
        var c = cipher();
        String encrypted = c.encrypt("原始值");
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> c.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("null 原样透传，便于处理尚未配置凭据的记录")
    void null原样透传() {
        var c = cipher();

        assertThat(c.encrypt(null)).isNull();
        assertThat(c.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("密钥长度不是 32 字节时启动即失败，而不是运行期才炸")
    void 密钥长度不对时构造即失败() {
        CryptoProperties props = new CryptoProperties();
        props.setKey(Base64.getEncoder().encodeToString("太短了".getBytes()));

        assertThatThrownBy(() -> new AesGcmCredentialCipher(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}
