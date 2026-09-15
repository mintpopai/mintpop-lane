package ai.mintpop.lane.config;

import ai.mintpop.lane.client.StripeGateway;
import ai.mintpop.lane.service.OrderNotifyService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * 支付装配：把「撤 Stripe 侧 intent」这个动作做成一个函数 bean 交给订单模块——
 * OrderService / OrderExpiryService 因此不直接依赖 Stripe SDK，测试里可换成记录器。
 */
@Configuration
public class PaymentConfig {

    @Bean
    Consumer<String> intentCanceller(StripeGateway stripeGateway) {
        return stripeGateway::cancelPaymentIntent;
    }

    /** 首次入账成功后（事务已提交）推飞书；@Async 在 OrderNotifyService 上，这里只是转发 */
    @Bean
    Consumer<String> orderSettledListener(OrderNotifyService orderNotifyService) {
        return orderNotifyService::notifyOrderPaid;
    }
}
