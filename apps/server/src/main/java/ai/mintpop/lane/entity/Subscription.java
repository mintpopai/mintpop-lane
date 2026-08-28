package ai.mintpop.lane.entity;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

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

    /**
     * 分配号：给用户看的分配标识，10 位 Crockford Base32 大写短码。
     * 用户界面与工单里用它指认「是哪一次分配」，程序内部引用仍走自增 id。
     */
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

    /** 本次分配给用户的账号邮箱，一律小写存储；NULL 表示未录 */
    private String accountEmail;

    /** 席位凭据的密文 */
    @ToString.Exclude
    private String credentialCipher;

    /** 服务端实际授予的 scope，空格分隔；为空表示旧式凭证 */
    private String credentialScope;

    /** Anthropic 侧 token 标识，审计用 */
    private String credentialTokenUuid;

    /** 凭证签发时刻 */
    private Instant credentialIssuedAt;

    /** 凭证到期时刻 */
    private Instant credentialExpiresAt;

    /** refresh_token 密文。第一版不使用，排除出 toString */
    @ToString.Exclude
    private String credentialRefreshCipher;

    private String remark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
