package ai.mintpop.lane.entity;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.enumeration.OrderStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * plan_order 表映射：用户在控制台自助购买套餐的订单。全部字段明文，不设 DTO，业务层直接用实体。
 * 套餐信息落快照，套餐后续改名改价甚至硬删都不影响本单。
 */
@Data
@TableName("plan_order")
public class PlanOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号，给用户看、也写入 Stripe metadata.orderId */
    private String orderNo;

    private Long userId;

    /** 弱引用 plan(id)，允许悬空 */
    private Long planId;

    private String name;

    private AgentType agentType;

    private Integer planDurationDays;

    private BigDecimal planPrice;

    private Currency planCurrency;

    /** 应付金额，最小货币单位整数 */
    private Long amountMinor;

    private OrderStatus status;

    /** 固定 stripe；未发起支付为 null */
    private String paymentProvider;

    /** Stripe PaymentIntent id */
    private String paymentTradeNo;

    private Instant paidAt;

    /** 履约建出的订阅 id；PAID 后必有 */
    private Long subscriptionId;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
