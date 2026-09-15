package ai.mintpop.lane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/** 支付相关装配。撤 intent 的动作在 Task 5 接上 StripeGateway，这里先给一个空实现占位 */
@Configuration
public class PaymentConfig {

    @Bean
    Consumer<String> intentCanceller() {
        return intentId -> { };
    }
}
