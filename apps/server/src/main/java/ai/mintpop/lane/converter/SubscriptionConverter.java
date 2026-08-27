package ai.mintpop.lane.converter;

import ai.mintpop.lane.crypto.CredentialCipher;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.Subscription;
import org.springframework.stereotype.Component;

/**
 * 订阅的 entity ↔ dto 转换。读路径（{@link #toDto}/{@link #toEntity}）的加解密收口在这里；
 * 写路径另有 {@code SubscriptionRepository.updateCredential}/{@code clearCredentialMetadata}
 * 两个直接收密文的元数据专用方法（由 CredentialIssueServiceImpl 注入 CredentialCipher 自行加密），
 * 是有意为之的例外，不经过本转换器。
 */
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
        dto.setEnterpriseId(entity.getEnterpriseId());
        dto.setAgentType(entity.getAgentType());
        dto.setPlanId(entity.getPlanId());
        dto.setName(entity.getName());
        dto.setPlanDurationDays(entity.getPlanDurationDays());
        dto.setPlanPrice(entity.getPlanPrice());
        dto.setPlanCurrency(entity.getPlanCurrency());
        dto.setStartsAt(entity.getStartsAt());
        dto.setEndsAt(entity.getEndsAt());
        dto.setAccountEmail(entity.getAccountEmail());
        dto.setCredential(decrypt(entity.getCredentialCipher()));
        // 只读方向映射：写回交给专用的 updateCredential/clearCredentialMetadata，
        // toEntity() 故意不回填它，常规 update() 的 SQL 也不含这一列
        dto.setCredentialExpiresAt(entity.getCredentialExpiresAt());
        // 同上，只读方向映射：toEntity() 故意不回填它
        dto.setCredentialScope(entity.getCredentialScope());
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
        entity.setEnterpriseId(dto.getEnterpriseId());
        entity.setAgentType(dto.getAgentType());
        entity.setPlanId(dto.getPlanId());
        entity.setName(dto.getName());
        entity.setPlanDurationDays(dto.getPlanDurationDays());
        entity.setPlanPrice(dto.getPlanPrice());
        entity.setPlanCurrency(dto.getPlanCurrency());
        entity.setStartsAt(dto.getStartsAt());
        entity.setEndsAt(dto.getEndsAt());
        entity.setAccountEmail(dto.getAccountEmail());
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
