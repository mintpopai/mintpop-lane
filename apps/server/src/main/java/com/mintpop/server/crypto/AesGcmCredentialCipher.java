package com.mintpop.server.crypto;

import com.mintpop.server.config.CryptoProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 实现。存储形态为 Base64(IV || 密文 || 认证标签)：
 * IV 每次随机，因此同一明文的密文各不相同；认证标签保证密文被篡改时解密必失败。
 */
@Component
public class AesGcmCredentialCipher implements CredentialCipher {

    /** GCM 推荐的 IV 长度（字节） */
    private static final int IV_LENGTH = 12;

    /** 认证标签长度（位） */
    private static final int TAG_LENGTH_BITS = 128;

    private static final int KEY_LENGTH = 32;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCredentialCipher(CryptoProperties properties) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.getKey());
        } catch (RuntimeException e) {
            throw new IllegalStateException("mintpop.crypto.key 不是合法的 Base64", e);
        }
        if (key.length != KEY_LENGTH) {
            throw new IllegalStateException(
                    "mintpop.crypto.key 解码后必须是 32 字节，实际为 " + key.length + " 字节");
        }
        this.keySpec = new SecretKeySpec(key, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 不把原始异常信息透出给调用方，避免密文细节进日志之外的地方
            throw new IllegalStateException("解密失败：密文不可信或密钥不匹配", e);
        }
    }
}
