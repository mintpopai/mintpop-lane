package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;

import java.time.Instant;

/** 管理端的订阅视图。凭据只以 hasCredential 表达「有没有录」，本体一个字符都不回传。 */
public record AdminSubscriptionResponse(
        Long id,
        Long userId,
        AgentType agentType,
        String name,
        Instant startsAt,
        Instant endsAt,
        boolean hasCredential,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
