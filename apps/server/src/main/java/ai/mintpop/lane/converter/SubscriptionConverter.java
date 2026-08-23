package ai.mintpop.lane.converter;

import ai.mintpop.lane.crypto.CredentialCipher;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.Subscription;
import org.springframework.stereotype.Component;

/** 订阅的 entity ↔ dto 转换。席位凭据的加解密只发生在这里。 */
@Component
public class SubscriptionConverter {

    private final CredentialCipher cipher;

    public SubscriptionConverter(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    public SubscriptionDto toDto(Subscription entity) {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setId(entity.getId());
        dto.setAssignmentNo(entity.getAssignmentNo());
        dto.setUserId(entity.getUserId());
        dto.setAgentType(entity.getAgentType());
        dto.setPlanId(entity.getPlanId());
        dto.setName(entity.getName());
        dto.setPlanDurationDays(entity.getPlanDurationDays());
        dto.setPlanPrice(entity.getPlanPrice());
        dto.setPlanCurrency(entity.getPlanCurrency());
        dto.setStartsAt(entity.getStartsAt());
        dto.setEndsAt(entity.getEndsAt());
        dto.setCredential(decrypt(entity.getCredentialCipher()));
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    public Subscription toEntity(SubscriptionDto dto) {
        Subscription entity = new Subscription();
        entity.setId(dto.getId());
        entity.setAssignmentNo(dto.getAssignmentNo());
        entity.setUserId(dto.getUserId());
        entity.setAgentType(dto.getAgentType());
        entity.setPlanId(dto.getPlanId());
        entity.setName(dto.getName());
        entity.setPlanDurationDays(dto.getPlanDurationDays());
        entity.setPlanPrice(dto.getPlanPrice());
        entity.setPlanCurrency(dto.getPlanCurrency());
        entity.setStartsAt(dto.getStartsAt());
        entity.setEndsAt(dto.getEndsAt());
        String credential = dto.getCredential();
        entity.setCredentialCipher(
                credential == null || credential.isBlank() ? null : cipher.encrypt(credential));
        entity.setRemark(dto.getRemark());
        return entity;
    }

    /**
     * 密文为 null 或空白时直接返回 null，不进解密——空串走 Base64 解码会炸。
     * 只兜「空白」：密钥不匹配等真正的解密失败继续大声抛错（同 UserConverter 原先的取舍）。
     */
    private String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        return cipher.decrypt(cipherText);
    }
}
