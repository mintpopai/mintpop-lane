package ai.mintpop.lane.service;

import ai.mintpop.lane.config.OrderProperties;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.repository.PlanOrderRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * 订单懒惰过期：无定时任务，读到超时未支付订单的入口（列表 / 单笔 / 发起支付 / verify）
 * 顺手把它置 EXPIRED，并尽力而为撤掉 Stripe 侧的 PaymentIntent（令残留支付页失效）。
 * 只有 PENDING / FAILED 可过期；条件 UPDATE 防与入账 / 取消竞态，钱已收仍由 settlePaid 入账兜底。
 */
@Service
public class OrderExpiryService {

    private final PlanOrderRepository orderRepository;
    private final OrderProperties properties;
    private final Clock clock;
    private final Consumer<String> intentCanceller;

    /**
     * @param intentCanceller 撤 Stripe 侧 intent 的动作，收 PaymentIntent id；由 PaymentConfig 接到
     *                        StripeGateway.cancelPaymentIntent 上（Task 5）。做成注入点是为了本服务
     *                        不直接依赖 Stripe SDK，测试里可换成记录器。
     */
    public OrderExpiryService(PlanOrderRepository orderRepository, OrderProperties properties, Clock clock,
                              Consumer<String> intentCanceller) {
        this.orderRepository = orderRepository;
        this.properties = properties;
        this.clock = clock;
        this.intentCanceller = intentCanceller;
    }

    /** 单笔：超时则置 EXPIRED 并撤 intent，返回是否已超时（不看 UPDATE 是否生效——超时的单一律不可再付） */
    public boolean expireIfTimedOut(PlanOrder order) {
        if (!order.getStatus().isPayable() || order.getCreatedAt() == null
                || order.getCreatedAt().isAfter(cutoff())) {
            return false;
        }
        expire(order);
        return true;
    }

    /** 批量：某用户全部超时的可支付订单逐单过期（列表入口用） */
    public void expireTimedOut(Long userId) {
        orderRepository.findTimedOut(userId, cutoff()).forEach(this::expire);
    }

    /** 剩余支付秒数，已超时为 0 不出负数；createdAt 为空按整个时限算 */
    public long remainingSeconds(PlanOrder order) {
        long limit = properties.getExpireMinutes() * 60;
        if (order.getCreatedAt() == null) {
            return limit;
        }
        long elapsed = Duration.between(order.getCreatedAt(), clock.instant()).getSeconds();
        return Math.max(0, limit - elapsed);
    }

    private void expire(PlanOrder order) {
        if (orderRepository.markExpired(order.getOrderNo()) && order.getPaymentTradeNo() != null) {
            intentCanceller.accept(order.getPaymentTradeNo());
        }
    }

    private Instant cutoff() {
        return clock.instant().minus(Duration.ofMinutes(properties.getExpireMinutes()));
    }
}
