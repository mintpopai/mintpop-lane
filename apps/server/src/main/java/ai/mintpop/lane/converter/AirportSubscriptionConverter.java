package ai.mintpop.lane.converter;

import ai.mintpop.lane.crypto.CredentialCipher;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.entity.AirportSubscription;
import org.springframework.stereotype.Component;

/** 机场订阅的 entity ↔ dto 转换。订阅链接的加解密只发生在这里。 */
@Component
public class AirportSubscriptionConverter {

    private final CredentialCipher cipher;

    public AirportSubscriptionConverter(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    public AirportSubscriptionDto toDto(AirportSubscription entity) {
        AirportSubscriptionDto dto = new AirportSubscriptionDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSubUrl(cipher.decrypt(entity.getSubUrlCipher()));
        dto.setRemark(entity.getRemark());
        dto.setUsedBytes(entity.getTrafficUsedBytes());
        dto.setTotalBytes(entity.getTrafficTotalBytes());
        dto.setExpiresAt(entity.getTrafficExpiresAt());
        dto.setTrafficAlertedPct(entity.getTrafficAlertedPct());
        dto.setFetchedAt(entity.getFetchedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public AirportSubscription toEntity(AirportSubscriptionDto dto) {
        AirportSubscription entity = new AirportSubscription();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setSubUrlCipher(cipher.encrypt(dto.getSubUrl()));
        entity.setRemark(dto.getRemark());
        entity.setTrafficUsedBytes(dto.getUsedBytes());
        entity.setTrafficTotalBytes(dto.getTotalBytes());
        entity.setTrafficExpiresAt(dto.getExpiresAt());
        entity.setTrafficAlertedPct(dto.getTrafficAlertedPct());
        entity.setFetchedAt(dto.getFetchedAt());
        return entity;
    }
}
