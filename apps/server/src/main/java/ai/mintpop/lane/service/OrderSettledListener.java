package ai.mintpop.lane.service;

/**
 * 订单首次入账成功（事务已提交）后的回调。由 PaymentConfig 接到 OrderNotifyService 推飞书；
 * 重放的入账不会再次触发。
 */
@FunctionalInterface
public interface OrderSettledListener {

    void onSettled(String orderNo);
}
