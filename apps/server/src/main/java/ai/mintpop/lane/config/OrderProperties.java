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

    /**
     * 同一用户同时可持有的未支付（PENDING / FAILED）订单上限，达到后拒绝再下单。
     * 防刷单占库；正常用户付款失败重试走同一张单，不会新建，用不到第二张。
     */
    private int maxPayablePerUser = 5;
}
