package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.AgentType;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/** 订阅的明文领域对象。凭据在这里是明文，密文只存在于 entity 与 converter 之间。 */
@Data
public class SubscriptionDto {

    private Long id;

    private Long userId;

    private AgentType agentType;

    /** 用户可见的套餐名 */
    private String name;

    private Instant startsAt;

    private Instant endsAt;

    /** 席位凭据（明文）。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private String credential;

    private String remark;

    private Instant createdAt;

    private Instant updatedAt;

    /** 在期判定：起含止不含。判定点在拉链路与心跳里，纯查询、无定时任务 */
    public boolean isActiveAt(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }
}
