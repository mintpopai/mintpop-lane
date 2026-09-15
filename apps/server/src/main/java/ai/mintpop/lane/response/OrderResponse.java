package ai.mintpop.lane.response;

import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.enumeration.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 用户侧订单视图 */
public record OrderResponse(
        String orderNo,
        String name,
        AgentType agentType,
        Integer planDurationDays,
        BigDecimal planPrice,
        Currency planCurrency,
        Long amountMinor,
        OrderStatus status,
        Instant paidAt,
        /** 履约建出的订阅 id；未支付为 null */
        Long subscriptionId,
        Instant createdAt
) {
    public static OrderResponse from(PlanOrder o) {
        return new OrderResponse(o.getOrderNo(), o.getName(), o.getAgentType(), o.getPlanDurationDays(),
                o.getPlanPrice(), o.getPlanCurrency(), o.getAmountMinor(), o.getStatus(), o.getPaidAt(),
                o.getSubscriptionId(), o.getCreatedAt());
    }
}
