package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.OrderStatus;
import ai.mintpop.lane.mapper.PlanOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 套餐订单的 MySQL 实现。 */
@Repository
public class MybatisPlanOrderRepository implements PlanOrderRepository {

    private final PlanOrderMapper mapper;

    public MybatisPlanOrderRepository(PlanOrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long create(PlanOrder order) {
        order.setId(null);
        mapper.insert(order);
        return order.getId();
    }

    @Override
    public Optional<PlanOrder> findByOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PlanOrder>lambdaQuery()
                .eq(PlanOrder::getOrderNo, orderNo)));
    }

    @Override
    public List<PlanOrder> findByUserId(Long userId, int limit) {
        return mapper.selectList(Wrappers.<PlanOrder>lambdaQuery()
                .eq(PlanOrder::getUserId, userId)
                .orderByDesc(PlanOrder::getCreatedAt)
                .orderByDesc(PlanOrder::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public List<PlanOrder> findTimedOut(Long userId, Instant cutoff) {
        return mapper.selectList(Wrappers.<PlanOrder>lambdaQuery()
                .eq(PlanOrder::getUserId, userId)
                .in(PlanOrder::getStatus, OrderStatus.PENDING, OrderStatus.FAILED)
                .lt(PlanOrder::getCreatedAt, cutoff));
    }

    @Override
    public long countPayable(Long userId) {
        return mapper.selectCount(Wrappers.<PlanOrder>lambdaQuery()
                .eq(PlanOrder::getUserId, userId)
                .in(PlanOrder::getStatus, OrderStatus.PENDING, OrderStatus.FAILED));
    }

    @Override
    public boolean attachPaymentIntent(Long id, String provider, String intentId) {
        return mapper.update(null, Wrappers.<PlanOrder>lambdaUpdate()
                .eq(PlanOrder::getId, id)
                .isNull(PlanOrder::getPaymentTradeNo)
                .set(PlanOrder::getPaymentProvider, provider)
                .set(PlanOrder::getPaymentTradeNo, intentId)) > 0;
    }

    @Override
    public boolean markPaid(String orderNo, String intentId, Instant paidAt) {
        return mapper.update(null, Wrappers.<PlanOrder>lambdaUpdate()
                .eq(PlanOrder::getOrderNo, orderNo)
                .in(PlanOrder::getStatus, OrderStatus.PENDING, OrderStatus.FAILED,
                        OrderStatus.CANCELLED, OrderStatus.EXPIRED)
                .set(PlanOrder::getStatus, OrderStatus.PAID)
                .set(PlanOrder::getPaidAt, paidAt)
                .set(PlanOrder::getPaymentProvider, "stripe")
                .set(PlanOrder::getPaymentTradeNo, intentId)) > 0;
    }

    @Override
    public boolean markFailed(String orderNo) {
        return transition(orderNo, OrderStatus.FAILED, OrderStatus.PENDING);
    }

    @Override
    public boolean markCancelled(String orderNo) {
        return transition(orderNo, OrderStatus.CANCELLED, OrderStatus.PENDING, OrderStatus.FAILED);
    }

    @Override
    public boolean markExpired(String orderNo) {
        return transition(orderNo, OrderStatus.EXPIRED, OrderStatus.PENDING, OrderStatus.FAILED);
    }

    @Override
    public void attachSubscription(Long id, Long subscriptionId) {
        mapper.update(null, Wrappers.<PlanOrder>lambdaUpdate()
                .eq(PlanOrder::getId, id)
                .set(PlanOrder::getSubscriptionId, subscriptionId));
    }

    /** 条件迁移：只有处于 from 之一时才置为 to，影响行数即是否生效 */
    private boolean transition(String orderNo, OrderStatus to, OrderStatus... from) {
        return mapper.update(null, Wrappers.<PlanOrder>lambdaUpdate()
                .eq(PlanOrder::getOrderNo, orderNo)
                .in(PlanOrder::getStatus, (Object[]) from)
                .set(PlanOrder::getStatus, to)) > 0;
    }
}
