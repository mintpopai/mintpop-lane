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
        /** 该订阅的分配号，用户向客服报障时引用；无订阅（或订阅已被删）为 null */
        String assignmentNo,
        Instant createdAt
) {
    /** @param assignmentNo 由调用方按 subscriptionId 查好传入，本方法不查库 */
    public static OrderResponse from(PlanOrder o, String assignmentNo) {
        return new OrderResponse(o.getOrderNo(), o.getName(), o.getAgentType(), o.getPlanDurationDays(),
                o.getPlanPrice(), o.getPlanCurrency(), o.getAmountMinor(), o.getStatus(), o.getPaidAt(),
                o.getSubscriptionId(), assignmentNo, o.getCreatedAt());
    }
}
