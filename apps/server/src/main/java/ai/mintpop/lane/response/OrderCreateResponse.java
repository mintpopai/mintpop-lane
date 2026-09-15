package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.Currency;

/** 下单结果：前端拿 orderNo 跳支付页 */
public record OrderCreateResponse(String orderNo, Long amountMinor, Currency currency) {
}
