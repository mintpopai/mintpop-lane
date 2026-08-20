package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;

import java.time.LocalDateTime;

/** 管理端的订阅视图。凭据只以 hasCredential 表达「有没有录」，本体一个字符都不回传。 */
public record AdminSubscriptionResponse(
        Long id,
        Long userId,
        AgentType agentType,
        String name,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean hasCredential,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
