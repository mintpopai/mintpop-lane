package ai.mintpop.lane.service;

/**
 * 撤掉支付处理方一侧的 PaymentIntent（令残留支付页失效），尽力而为。
 * 由 PaymentConfig 接到 StripeGateway 上；订单模块只依赖本接口，不直接依赖 Stripe SDK，测试里可换成记录器。
 */
@FunctionalInterface
public interface PaymentIntentCanceller {

    /** @param intentId 处理方侧的 PaymentIntent id（即订单的 paymentTradeNo） */
    void cancel(String intentId);
}
