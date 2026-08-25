package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.UserRole;

import java.time.Instant;
import java.util.List;

/** 当前用户视图：桌面端启动验活与状态页用。凭据一个字符都不出现。 */
public record MeResponse(
        Long id,
        String email,
        UserRole role,
        List<MeSubscription> subscriptions
) {

    /** 订阅概览；active 为服务端按当前时间算好的在期标记 */
    public record MeSubscription(
            Long id,
            /** 分配号：给用户看的分配标识。买了多份同套餐时，只有它能把两份分辨开 */
            String assignmentNo,
            String name,
            AgentType agentType,
            Instant startsAt,
            Instant endsAt,
            boolean active
    ) {
    }
}
