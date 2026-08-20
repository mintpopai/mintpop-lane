package ai.mintpop.lane.entity;

import ai.mintpop.lane.enumeration.AgentType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * subscription 表映射。这是「带密文」的持久化形态，
 * 业务层一律使用 SubscriptionDto（明文），转换由 SubscriptionConverter 负责。
 */
@Data
@TableName("subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private AgentType agentType;

    /** 用户可见的套餐名 */
    private String name;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    /** 席位凭据的密文 */
    private String credentialCipher;

    private String remark;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
