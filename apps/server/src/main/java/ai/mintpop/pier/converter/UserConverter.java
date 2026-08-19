package ai.mintpop.pier.converter;

import ai.mintpop.pier.crypto.CredentialCipher;
import ai.mintpop.pier.dto.UserDto;
import ai.mintpop.pier.entity.User;
import org.springframework.stereotype.Component;

/**
 * 用户的 entity ↔ dto 转换。席位凭据的加解密只发生在这里。
 */
@Component
public class UserConverter {

    private final CredentialCipher cipher;

    public UserConverter(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    public UserDto toDto(User entity) {
        UserDto dto = new UserDto();
        dto.setId(entity.getId());
        dto.setSubject(entity.getSubject());
        dto.setName(entity.getName());
        dto.setRole(entity.getRole());
        dto.setStatus(entity.getStatus());
        dto.setFrontNodeId(entity.getFrontNodeId());
        dto.setLandNodeId(entity.getLandNodeId());
        dto.setClaudeCredential(decrypt(entity.getClaudeCredentialCipher()));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public User toEntity(UserDto dto) {
        User entity = new User();
        entity.setId(dto.getId());
        entity.setSubject(dto.getSubject());
        entity.setName(dto.getName());
        entity.setRole(dto.getRole());
        entity.setStatus(dto.getStatus());
        entity.setFrontNodeId(dto.getFrontNodeId());
        entity.setLandNodeId(dto.getLandNodeId());
        String credential = dto.getClaudeCredential();
        entity.setClaudeCredentialCipher(
                credential == null || credential.isBlank() ? null : cipher.encrypt(credential));
        return entity;
    }

    /**
     * 密文为 null 或空白（手工改库、数据导入等异常场景）时直接返回 null，不进解密——
     * 空串走 Base64 解码会在 arraycopy 处炸，导致该用户所有读路径 500。
     * 注意只兜「空白」这一种情况：密钥不匹配等真正的解密失败必须继续大声抛错，
     * 静默吞掉会让「密钥配错」退化成「所有人都没凭据」这种更难排查的故障。
     */
    private String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        return cipher.decrypt(cipherText);
    }
}
