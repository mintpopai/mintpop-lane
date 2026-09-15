package ai.mintpop.lane.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPropertiesTest {

    @Test
    @DisplayName("我方命名经映射表转 Stripe 类型，顺序保留、去重；空配置回退 card")
    void resolvesPaymentMethodTypes() {
        PaymentProperties p = new PaymentProperties();
        assertThat(p.resolvePaymentMethodTypes()).containsExactly("card", "alipay", "wechat_pay");
        p.setSupportedTypes(" wxpay , card , wxpay , unknown ");
        assertThat(p.resolvePaymentMethodTypes()).containsExactly("wechat_pay", "card");
        p.setSupportedTypes("");
        assertThat(p.resolvePaymentMethodTypes()).containsExactly("card");
    }

    @Test
    @DisplayName("secret key 与 publishable key 都有值才算已配置")
    void configuredRequiresBothKeys() {
        PaymentProperties p = new PaymentProperties();
        assertThat(p.isConfigured()).isFalse();
        p.setSecretKey("s");
        assertThat(p.isConfigured()).isFalse();
        p.setPublishableKey("p");
        assertThat(p.isConfigured()).isTrue();
    }
}
