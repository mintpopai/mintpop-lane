package ai.mintpop.lane.entity;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * subscription 表映射。这是「带密文」的持久化形态，
 * 业务层一律使用 SubscriptionDto（明文），转换由 SubscriptionConverter 负责。
 */
@Data
@TableName("subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分配号：本次分配的唯一业务标识（32 位十六进制 UUID），对外引用一律用它 */
    private String assignmentNo;

    private Long userId;

    /** 归属企业 id；NULL 表示个人订阅。弱引用，不设外键，删除把关在服务层 */
    private Long enterpriseId;

    private AgentType agentType;

    /** 所选套餐 id。弱引用，套餐硬删后允许悬空，历史呈现以快照字段为准 */
    private Long planId;

    /** 用户可见的套餐名，分配时从所选套餐快照 */
    private String name;

    /** 套餐时长快照（天）：止期 = 起期 + 本值 */
    private Integer planDurationDays;

    /** 套餐价格快照 */
    private BigDecimal planPrice;

    /** 套餐币种快照 */
    private Currency planCurrency;

    private Instant startsAt;

    private Instant endsAt;

    /** 席位凭据的密文 */
    private String credentialCipher;

    private String remark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
