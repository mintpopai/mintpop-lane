package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 订单业务配置（order.*） */
@Data
@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    /** 待支付订单的支付时限（分钟）：超过后由读到它的入口懒惰置为 EXPIRED，不再受理支付 */
    private long expireMinutes = 30;
}
