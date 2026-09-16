package ai.mintpop.lane.response;

import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;

import java.math.BigDecimal;

/** 用户侧的套餐视图：只有购买决策要看的字段，管理员备注与上架状态不出现 */
public record UserPlanResponse(
        Long id,
        String name,
        AgentType agentType,
        Integer durationDays,
        BigDecimal price,
        Currency currency,
        /** 面向用户的短描述，可空 */
        String description,
        /** 套餐图公开 URL，可空 */
        String imageUrl
) {
    public static UserPlanResponse from(Plan plan) {
        return new UserPlanResponse(plan.getId(), plan.getName(), plan.getAgentType(),
                plan.getDurationDays(), plan.getPrice(), plan.getCurrency(),
                plan.getDescription(), plan.getImageUrl());
    }
}
