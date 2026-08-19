package ai.mintpop.lane.converter;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.User;
import org.springframework.stereotype.Component;

/** 用户的 entity ↔ dto 转换。用户表已不含密文字段，这里是纯字段搬运。 */
@Component
public class UserConverter {

    public UserDto toDto(User entity) {
        UserDto dto = new UserDto();
        dto.setId(entity.getId());
        dto.setSubject(entity.getSubject());
        dto.setEmail(entity.getEmail());
        dto.setName(entity.getName());
        dto.setRole(entity.getRole());
        dto.setStatus(entity.getStatus());
        dto.setFrontNodeId(entity.getFrontNodeId());
        dto.setLandNodeId(entity.getLandNodeId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public User toEntity(UserDto dto) {
        User entity = new User();
        entity.setId(dto.getId());
        entity.setSubject(dto.getSubject());
        entity.setEmail(dto.getEmail());
        entity.setName(dto.getName());
        entity.setRole(dto.getRole());
        entity.setStatus(dto.getStatus());
        entity.setFrontNodeId(dto.getFrontNodeId());
        entity.setLandNodeId(dto.getLandNodeId());
        return entity;
    }
}
