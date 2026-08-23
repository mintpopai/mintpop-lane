package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/** 管理端的套餐视图 */
public record PlanResponse(
        Long id,
        String name,
        /** 本套餐面向的 agent 类型 */
        AgentType agentType,
        /** 套餐时长（天） */
        Integer durationDays,
        BigDecimal price,
        Currency currency,
        /** 上架状态：false 表示停用但保留 */
        Boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
