package ai.mintpop.lane.enumeration;

/**
 * 套餐订单状态。PAID 即终态：付款入账与建订阅在同一事务完成，没有单独的履约中间态。
 * 前端 console 的 types.ts 有逐字镜像，改这里两端同步。
 */
public enum OrderStatus {
    /** 待支付 */
    PENDING,
    /** 已支付，订阅已建出（待开通） */
    PAID,
    /** 用户主动取消 */
    CANCELLED,
    /** 超时未付，由读到它的入口懒惰置入 */
    EXPIRED,
    /** 最近一次支付尝试失败（卡被拒等），同一 PaymentIntent 可继续重试 */
    FAILED;

    /** 还能发起或继续支付的状态 */
    public boolean isPayable() {
        return this == PENDING || this == FAILED;
    }
}
