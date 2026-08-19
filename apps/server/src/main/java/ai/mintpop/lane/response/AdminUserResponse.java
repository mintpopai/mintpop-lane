package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端的用户视图。 */
public record AdminUserResponse(
        Long id,
        String subject,
        String email,
        String name,
        UserRole role,
        UserStatus status,
        Long frontNodeId,
        String frontNodeName,
        Long landNodeId,
        String landNodeName,
        /** 该用户的期望出口 IP，取自其落地节点 */
        List<String> egressIps,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
