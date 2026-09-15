package ai.mintpop.lane.service;

import ai.mintpop.lane.client.StripeGateway;
import ai.mintpop.lane.client.StripeWebhookEvent;
import ai.mintpop.lane.config.PaymentProperties;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.PlanOrder;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.PlanOrderRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.response.CheckoutInfoResponse;
import ai.mintpop.lane.response.PaymentIntentResponse;
import ai.mintpop.lane.response.VerifyOrderResponse;
import ai.mintpop.lane.util.AssignmentNo;
import com.stripe.model.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 支付：后端只有单一 stripe 通道（PaymentIntent 模式）；微信 / 支付宝 / 银行卡只存在于前端展示层。
 * webhook 是成单唯一真相源，verify 只是主动查一次结果；两者共用 settlePaid，幂等靠条件 UPDATE。
 */
@Slf4j
@Service
public class PaymentService {

    static final String PROVIDER_STRIPE = "stripe";
    private static final String EVENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String EVENT_FAILED = "payment_intent.payment_failed";
    private static final int ASSIGNMENT_NO_MAX_ATTEMPTS = 5;

    private final PlanOrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OrderService orderService;
    private final OrderExpiryService expiryService;
    private final StripeGateway stripeGateway;
    private final PaymentProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Consumer<String> orderSettledListener;
    private final Clock clock;

    /**
     * @param orderSettledListener 首次入账成功后（事务已提交）收到订单号；Task 8 接飞书通知。
     *                             做成注入点是为了本服务不依赖通知模块。
     */
    public PaymentService(PlanOrderRepository orderRepository, SubscriptionRepository subscriptionRepository,
                          OrderService orderService, OrderExpiryService expiryService, StripeGateway stripeGateway,
                          PaymentProperties properties, TransactionTemplate transactionTemplate,
                          Consumer<String> orderSettledListener, Clock clock) {
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.orderService = orderService;
        this.expiryService = expiryService;
        this.stripeGateway = stripeGateway;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.orderSettledListener = orderSettledListener;
        this.clock = clock;
    }

    /** 未配置时下发空方法列表，前端据此禁用支付入口——优雅降级而不是启动失败 */
    public CheckoutInfoResponse checkoutInfo() {
        if (!properties.isConfigured()) {
            return new CheckoutInfoResponse(List.of(), null);
        }
        return new CheckoutInfoResponse(List.of(PROVIDER_STRIPE), properties.getPublishableKey());
    }

    /** 懒创建 / 复用 PaymentIntent：新单与订单列表里「去支付」的旧单同一条路 */
    public PaymentIntentResponse getOrCreateIntent(Long userId, String orderNo) {
        PlanOrder order = orderService.requireOwn(userId, orderNo);
        if (!order.getStatus().isPayable() || expiryService.expireIfTimedOut(order)) {
            throw new BizException(BizCodeEnum.ORDER_NOT_PAYABLE);
        }
        // 网关是懒初始化的，配置缺失时它自己也会报 PAYMENT_NOT_CONFIGURED；这里提前判断
        // 是为了不依赖网关内部实现（测试里网关整体被替身，绕不过它的懒初始化检查）
        if (!properties.isConfigured()) {
            throw new BizException(BizCodeEnum.PAYMENT_NOT_CONFIGURED);
        }
        PaymentIntent intent;
        if (order.getPaymentTradeNo() == null) {
            intent = stripeGateway.createPaymentIntent(order.getOrderNo(), order.getAmountMinor(),
                    order.getPlanCurrency(), order.getName(), properties.resolvePaymentMethodTypes());
            if (!orderRepository.attachPaymentIntent(order.getId(), PROVIDER_STRIPE, intent.getId())) {
                // 并发发起支付：另一个请求已抢先落号，自己这个 intent 沦为孤儿——尽力撤掉，
                // 重新读单走「已有交易号」分支，让两个并发请求收敛到同一个 intent
                log.warn("并发发起支付，撤掉本请求创建的孤儿 intent orderNo={} orphanIntentId={}",
                        orderNo, intent.getId());
                stripeGateway.cancelPaymentIntent(intent.getId());
                order = orderService.requireOwn(userId, orderNo);
                intent = stripeGateway.retrievePaymentIntent(order.getPaymentTradeNo());
                if ("canceled".equals(intent.getStatus())) {
                    throw new BizException(BizCodeEnum.ORDER_NOT_PAYABLE);
                }
            }
        } else {
            intent = stripeGateway.retrievePaymentIntent(order.getPaymentTradeNo());
            if ("canceled".equals(intent.getStatus())) {
                // Stripe 侧已撤：本单已被取消或过期撤单，不能续付，引导重新下单
                throw new BizException(BizCodeEnum.ORDER_NOT_PAYABLE);
            }
        }
        return new PaymentIntentResponse(order.getOrderNo(), intent.getClientSecret(), order.getAmountMinor(),
                order.getPlanCurrency(), order.getName(), expiryService.remainingSeconds(order));
    }

    /** 主动向网关核实并推进状态（前端轮询用）。入账优先于过期：钱已收的订单不允许被判过期 */
    public VerifyOrderResponse verify(Long userId, String orderNo) {
        PlanOrder order = orderService.requireOwn(userId, orderNo);
        if (order.getStatus().isPayable() && order.getPaymentTradeNo() != null) {
            PaymentIntent intent = stripeGateway.retrievePaymentIntent(order.getPaymentTradeNo());
            if ("succeeded".equals(intent.getStatus())) {
                settlePaid(orderNo, intent.getId(), intent.getAmount(), intent.getCurrency());
                order = orderService.requireOwn(userId, orderNo);
                return new VerifyOrderResponse(order.getOrderNo(), order.getStatus());
            }
        }
        if (order.getStatus().isPayable() && expiryService.expireIfTimedOut(order)) {
            order = orderService.requireOwn(userId, orderNo);
        }
        return new VerifyOrderResponse(order.getOrderNo(), order.getStatus());
    }

    /** 处理 webhook 事件。查无此单 / 重放 / 无关事件一律静默，由控制器回 2xx 止住重试 */
    public void handleWebhook(StripeWebhookEvent event) {
        if (event.orderNo() == null) {
            return;
        }
        // 业务线认领：Stripe 事件是账户级广播，别的业务的事件跳过；无标记的旧事件放行走查单兜底
        if (event.product() != null && !properties.getProductCode().equals(event.product())) {
            log.debug("非本业务线事件，跳过 product={} orderNo={}", event.product(), event.orderNo());
            return;
        }
        switch (event.type()) {
            case EVENT_SUCCEEDED -> settlePaid(event.orderNo(), event.intentId(), event.amountMinor(), event.currency());
            case EVENT_FAILED -> orderRepository.markFailed(event.orderNo());
            default -> { /* 无关事件 */ }
        }
    }

    /**
     * 幂等入账 + 同事务履约：校验 provider、金额、币种 → 条件 UPDATE 置 PAID →
     * 生效时插入一条待开通订阅并回写订单。影响 0 行即已处理过（重放），静默返回。
     */
    public void settlePaid(String orderNo, String intentId, Long amountMinor, String currency) {
        PlanOrder order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) {
            // 与金额/币种不符同等严重：钱已收却核销不到单，都需要人工介入排查
            log.error("入账查无此单，忽略 orderNo={}", orderNo);
            return;
        }
        if (order.getPaymentTradeNo() != null && !order.getPaymentTradeNo().equals(intentId)) {
            log.error("入账交易号与订单不符，拒绝 orderNo={} 订单交易号={} 事件交易号={}",
                    orderNo, order.getPaymentTradeNo(), intentId);
            return;
        }
        if (!Objects.equals(amountMinor, order.getAmountMinor()) || currency == null
                || !order.getPlanCurrency().name().equalsIgnoreCase(currency)) {
            log.error("入账金额/币种与订单不符，拒绝 orderNo={} amount={} currency={} 订单金额={} 订单币种={}",
                    orderNo, amountMinor, currency, order.getAmountMinor(), order.getPlanCurrency());
            return;
        }
        Boolean settled = transactionTemplate.execute(status -> {
            if (!orderRepository.markPaid(orderNo, intentId, clock.instant())) {
                return false;
            }
            Long subscriptionId = createPendingSubscription(order);
            orderRepository.attachSubscription(order.getId(), subscriptionId);
            return true;
        });
        if (!Boolean.TRUE.equals(settled)) {
            log.info("入账重放（已处理过），忽略 orderNo={}", orderNo);
            return;
        }
        // 事务已提交；通知任务提交失败（如停机中执行器已关）也不能把 webhook 变 500
        try {
            orderSettledListener.accept(orderNo);
        } catch (Exception e) {
            log.warn("支付成功通知任务提交失败（不影响入账）orderNo={}", orderNo, e);
        }
    }

    /** 按订单快照建「待开通」订阅：起止为空，席位账号 / 企业 / 凭据都留给管理员开通时填 */
    private Long createPendingSubscription(PlanOrder order) {
        SubscriptionDto s = new SubscriptionDto();
        s.setUserId(order.getUserId());
        s.setPlanId(order.getPlanId());
        s.setName(order.getName());
        s.setAgentType(order.getAgentType());
        s.setPlanDurationDays(order.getPlanDurationDays());
        s.setPlanPrice(order.getPlanPrice());
        s.setPlanCurrency(order.getPlanCurrency());
        s.setRemark("自助购买，订单 " + order.getOrderNo());
        for (int attempt = 1; ; attempt++) {
            s.setAssignmentNo(AssignmentNo.generate());
            try {
                return subscriptionRepository.create(s);
            } catch (DuplicateKeyException e) {
                if (attempt >= ASSIGNMENT_NO_MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
