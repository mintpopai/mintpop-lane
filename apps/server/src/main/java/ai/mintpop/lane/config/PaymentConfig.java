package ai.mintpop.lane.config;

import ai.mintpop.lane.client.StripeGateway;
import ai.mintpop.lane.service.OrderNotifyService;
import ai.mintpop.lane.service.OrderSettledListener;
import ai.mintpop.lane.service.PaymentIntentCanceller;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付装配：把「撤 Stripe 侧 intent」「入账后通知」两个动作做成具名接口的 bean 交给订单模块——
 * OrderService / OrderExpiryService / PaymentService 因此不直接依赖 Stripe SDK 与通知实现，测试里可换成记录器。
 */
@Configuration
public class PaymentConfig {

    @Bean
    PaymentIntentCanceller intentCanceller(StripeGateway stripeGateway) {
        return stripeGateway::cancelPaymentIntent;
    }

    /** 首次入账成功后（事务已提交）推飞书；@Async 在 OrderNotifyService 上，这里只是转发 */
    @Bean
    OrderSettledListener orderSettledListener(OrderNotifyService orderNotifyService) {
        return orderNotifyService::notifyOrderPaid;
    }
}
