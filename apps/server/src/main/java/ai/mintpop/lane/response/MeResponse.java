package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.UserRole;

import java.time.LocalDateTime;
import java.util.List;

/** 当前用户视图：桌面端启动验活与状态页用。凭据一个字符都不出现。 */
public record MeResponse(
        Long id,
        String email,
        String name,
        UserRole role,
        List<MeSubscription> subscriptions
) {

    /** 订阅概览；active 为服务端按当前时间算好的在期标记 */
    public record MeSubscription(
            Long id,
            String name,
            AgentType agentType,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            boolean active
    ) {
    }
}
