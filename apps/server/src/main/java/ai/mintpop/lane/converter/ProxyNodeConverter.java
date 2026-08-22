package ai.mintpop.lane.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.crypto.CredentialCipher;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.ProxyNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 节点的 entity ↔ dto 转换。敏感键的加解密只发生在这里，
 * Service 与 Controller 层不接触密文。
 */
@Component
public class ProxyNodeConverter {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final CredentialCipher cipher;
    private final ObjectMapper objectMapper;

    public ProxyNodeConverter(CredentialCipher cipher, ObjectMapper objectMapper) {
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    public ProxyNodeDto toDto(ProxyNode entity) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRole(entity.getRole());
        dto.setProtocol(entity.getProtocol());
        dto.setServerAddr(entity.getServerAddr());
        dto.setPort(entity.getPort());
        dto.setExtraConfig(entity.getExtraConfig() == null ? Map.of() : entity.getExtraConfig());
        dto.setSecret(readSecret(entity.getSecretCipher()));
        dto.setEgressIp(entity.getEgressIp());
        dto.setEgressTimezone(entity.getEgressTimezone());
        dto.setCapacity(entity.getCapacity());
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        dto.setGroupId(entity.getGroupId());
        dto.setSourceName(entity.getSourceName());
        dto.setSourceType(entity.getSourceType());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public ProxyNode toEntity(ProxyNodeDto dto) {
        ProxyNode entity = new ProxyNode();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setRole(dto.getRole());
        entity.setProtocol(dto.getProtocol());
        entity.setServerAddr(dto.getServerAddr());
        entity.setPort(dto.getPort());
        entity.setExtraConfig(dto.getExtraConfig() == null ? Map.of() : dto.getExtraConfig());
        entity.setSecretCipher(writeSecret(dto.getSecret()));
        entity.setEgressIp(dto.getEgressIp());
        entity.setEgressTimezone(dto.getEgressTimezone());
        entity.setCapacity(dto.getCapacity());
        entity.setStatus(dto.getStatus());
        entity.setRemark(dto.getRemark());
        entity.setGroupId(dto.getGroupId());
        entity.setSourceName(dto.getSourceName());
        entity.setSourceType(dto.getSourceType());
        return entity;
    }

    private Map<String, Object> readSecret(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(cipher.decrypt(cipherText), MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("节点敏感键解密或反序列化失败", e);
        }
    }

    private String writeSecret(Map<String, Object> secret) {
        if (secret == null || secret.isEmpty()) {
            return null;
        }
        try {
            return cipher.encrypt(objectMapper.writeValueAsString(secret));
        } catch (Exception e) {
            throw new IllegalStateException("节点敏感键序列化或加密失败", e);
        }
    }
}
