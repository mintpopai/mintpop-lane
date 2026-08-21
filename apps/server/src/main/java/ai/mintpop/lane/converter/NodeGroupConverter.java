package ai.mintpop.lane.converter;

import ai.mintpop.lane.crypto.CredentialCipher;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.entity.NodeGroup;
import org.springframework.stereotype.Component;

/** 分组的 entity ↔ dto 转换。订阅链接的加解密只发生在这里。 */
@Component
public class NodeGroupConverter {

    private final CredentialCipher cipher;

    public NodeGroupConverter(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    public NodeGroupDto toDto(NodeGroup entity) {
        NodeGroupDto dto = new NodeGroupDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSubUrl(cipher.decrypt(entity.getSubUrlCipher()));
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public NodeGroup toEntity(NodeGroupDto dto) {
        NodeGroup entity = new NodeGroup();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setSubUrlCipher(cipher.encrypt(dto.getSubUrl()));
        entity.setRemark(dto.getRemark());
        return entity;
    }
}
