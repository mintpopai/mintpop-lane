package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.PlanOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 套餐订单的读写口。状态迁移一律是「条件 UPDATE + 返回是否生效」：
 * 入账、取消、过期之间的竞态全靠 WHERE 里的来源状态挡，服务层不做查-判-写。
 */
public interface PlanOrderRepository {

    /** 新建，返回自增主键。订单号撞唯一键抛 DuplicateKeyException，由调用方重试 */
    Long create(PlanOrder order);

    Optional<PlanOrder> findByOrderNo(String orderNo);

    /** 某用户的订单，按创建时间倒序（同秒按 id 倒序），最多 limit 条 */
    List<PlanOrder> findByUserId(Long userId, int limit);

    /** 某用户创建早于 cutoff 且仍可支付（PENDING / FAILED）的订单，供懒惰过期 */
    List<PlanOrder> findTimedOut(Long userId, Instant cutoff);

    /** 首次发起支付：落处理方与 PaymentIntent id */
    void attachPaymentIntent(Long id, String provider, String intentId);

    /**
     * 入账：来源 PENDING / FAILED / CANCELLED / EXPIRED → PAID。
     * 含 CANCELLED / EXPIRED 是设计决定：钱已收必须履约。返回 false 即已处理过（重放）。
     */
    boolean markPaid(String orderNo, String intentId, Instant paidAt);

    /** 支付尝试失败：仅 PENDING → FAILED */
    boolean markFailed(String orderNo);

    /** 用户取消：PENDING / FAILED → CANCELLED */
    boolean markCancelled(String orderNo);

    /** 懒惰过期：PENDING / FAILED → EXPIRED */
    boolean markExpired(String orderNo);

    /** 履约建出订阅后回写 */
    void attachSubscription(Long id, Long subscriptionId);
}
