package com.mintpop.server.converter;

import com.mintpop.server.crypto.CredentialCipher;
import com.mintpop.server.dto.UserDto;
import com.mintpop.server.entity.User;
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
        dto.setClaudeCredential(cipher.decrypt(entity.getClaudeCredentialCipher()));
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
}
