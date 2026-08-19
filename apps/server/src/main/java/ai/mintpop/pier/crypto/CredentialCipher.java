package ai.mintpop.pier.crypto;

/**
 * 敏感字段的加解密口。
 * 定成接口是为了让上层（converter）不绑定具体算法，也便于测试替换。
 */
public interface CredentialCipher {

    /** 明文 → 密文；入参为 null 时返回 null */
    String encrypt(String plaintext);

    /** 密文 → 明文；入参为 null 时返回 null，密文不可信时抛 IllegalStateException */
    String decrypt(String cipherText);
}
