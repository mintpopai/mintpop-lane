package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.enumeration.OrderStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.PlanOrderRepository;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.OrderCreateResponse;
import ai.mintpop.lane.response.OrderResponse;
import ai.mintpop.lane.util.OrderNo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** 用户侧订单：建单、查单、取消。支付在 PaymentService。 */
@Service
public class OrderService {

    /** 订单号撞唯一键后的重试次数上限 */
    private static final int ORDER_NO_MAX_ATTEMPTS = 5;
    /** 列表最多返回的条数：第一版不分页，一个人的订单到不了这个量 */
    static final int LIST_LIMIT = 100;

    private final PlanOrderRepository orderRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final OrderExpiryService expiryService;
    private final PaymentIntentCanceller intentCanceller;
    private final Clock clock;

    public OrderService(PlanOrderRepository orderRepository, PlanRepository planRepository,
                        UserRepository userRepository, OrderExpiryService expiryService,
                        PaymentIntentCanceller intentCanceller, Clock clock) {
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.expiryService = expiryService;
        this.intentCanceller = intentCanceller;
        this.clock = clock;
    }

    public OrderCreateResponse create(Long userId, Long planId) {
        UserDto user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.USER_NOT_ACTIVE);
        }
        Plan plan = planRepository.findById(planId)
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()))
                .orElseThrow(() -> new BizException(BizCodeEnum.PLAN_NOT_AVAILABLE));

        PlanOrder order = new PlanOrder();
        order.setUserId(userId);
        // 套餐信息落快照：之后改名改价甚至硬删都不影响这一单
        order.setPlanId(plan.getId());
        order.setName(plan.getName());
        order.setAgentType(plan.getAgentType());
        order.setPlanDurationDays(plan.getDurationDays());
        order.setPlanPrice(plan.getPrice());
        order.setPlanCurrency(plan.getCurrency());
        order.setAmountMinor(plan.getCurrency().toMinorUnit(plan.getPrice()));
        order.setStatus(OrderStatus.PENDING);
        createWithUniqueOrderNo(order);
        return new OrderCreateResponse(order.getOrderNo(), order.getAmountMinor(), order.getPlanCurrency());
    }

    /** 订单号是短随机段，撞唯一键就换一个再试；试满仍撞说明撞的不是订单号，异常照抛 */
    private void createWithUniqueOrderNo(PlanOrder order) {
        for (int attempt = 1; ; attempt++) {
            order.setOrderNo(OrderNo.generate(clock.instant()));
            try {
                orderRepository.create(order);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt >= ORDER_NO_MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    public List<OrderResponse> listMine(Long userId) {
        // 懒惰过期：先把该用户超时的可支付订单置 EXPIRED，列表读到的即是最新状态
        expiryService.expireTimedOut(userId);
        return orderRepository.findByUserId(userId, LIST_LIMIT).stream().map(OrderResponse::from).toList();
    }

    public OrderResponse getMine(Long userId, String orderNo) {
        PlanOrder order = requireOwn(userId, orderNo);
        if (expiryService.expireIfTimedOut(order)) {
            order = requireOwn(userId, orderNo);
        }
        return OrderResponse.from(order);
    }

    /** 仅 PENDING / FAILED 可取消；条件 UPDATE 影响 0 行即状态不允许。取消生效后尽力撤 Stripe 侧 intent */
    public void cancel(Long userId, String orderNo) {
        PlanOrder order = requireOwn(userId, orderNo);
        if (expiryService.expireIfTimedOut(order) || !orderRepository.markCancelled(orderNo)) {
            throw new BizException(BizCodeEnum.ORDER_NOT_CANCELLABLE);
        }
        if (order.getPaymentTradeNo() != null) {
            intentCanceller.cancel(order.getPaymentTradeNo());
        }
    }

    /** 按单号取订单并校验归属；查无或非本人一律「订单不存在」，不泄露他人单号的存在性 */
    public PlanOrder requireOwn(Long userId, String orderNo) {
        return orderRepository.findByOrderNo(orderNo)
                .filter(o -> Objects.equals(o.getUserId(), userId))
                .orElseThrow(() -> new BizException(BizCodeEnum.ORDER_NOT_FOUND));
    }
}
