package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.Currency;

/** 支付意图：前端拿 clientSecret 走 Stripe.js 确认，其余供支付页摘要与倒计时 */
public record PaymentIntentResponse(
        String orderNo,
        String clientSecret,
        Long amountMinor,
        Currency currency,
        /** 套餐名快照 */
        String productName,
        /** 剩余支付秒数，已超时为 0 */
        Long expireRemainingSeconds
) {
}
