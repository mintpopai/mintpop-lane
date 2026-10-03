package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** user_front_subscription 表映射：用户第一跳列表的一项 */
@Data
@TableName("user_front_subscription")
public class UserFrontSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 顺位：0 主用，1、2 备用 */
    private Integer position;

    private Long airportSubscriptionId;

    /** 是否管理员手动指定；同一用户各行取值相同 */
    private Boolean manuallyAssigned;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
}
