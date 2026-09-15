package ai.mintpop.lane.client;

/**
 * Stripe webhook 事件值对象：网关层验签后解出业务关心的最小字段。
 * 事件不携带 PaymentIntent（非支付类事件）时 intentId 及其后字段为 null。
 */
public record StripeWebhookEvent(
        String type,
        String intentId,
        /** 我方订单号（metadata.orderId） */
        String orderNo,
        /** 业务线标记（metadata.product） */
        String product,
        /** 金额（最小货币单位） */
        Long amountMinor,
        /** 币种（Stripe 回传为小写 ISO） */
        String currency
) {
}
