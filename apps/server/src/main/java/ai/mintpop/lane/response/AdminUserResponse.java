package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;

import java.time.Instant;
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
        /** 该用户的期望出口 IP，取自其落地节点；未分配或落地未填出口时为 null */
        String egressIp,
        /** 在期订阅摘要，供列表一眼看出这个人开了什么、到什么时候 */
        List<ActiveSubscriptionBrief> activeSubscriptions,
        Instant createdAt,
        Instant updatedAt
) {

    /** 在期订阅摘要，供列表一眼看出这个人开了什么、到什么时候 */
    public record ActiveSubscriptionBrief(Long id, String name, AgentType agentType, Instant endsAt) {
    }
}
