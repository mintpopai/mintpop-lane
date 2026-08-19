package ai.mintpop.pier.response;

import ai.mintpop.pier.enumeration.UserRole;
import ai.mintpop.pier.enumeration.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端的用户视图。
 * 席位凭据只以 credentialConfigured 表达「有没有配」，凭据本身一个字符都不回传。
 */
public record AdminUserResponse(
        Long id,
        String subject,
        String name,
        UserRole role,
        UserStatus status,
        Long frontNodeId,
        String frontNodeName,
        Long landNodeId,
        String landNodeName,
        /** 该用户的期望出口 IP，取自其落地节点 */
        List<String> egressIps,
        boolean credentialConfigured,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
