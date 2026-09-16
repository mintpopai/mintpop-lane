package ai.mintpop.lane.entity;

import ai.mintpop.lane.enumeration.RebindRequestStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * device_rebind_request 表映射。全部字段明文，不设 DTO。
 */
@Data
@TableName("device_rebind_request")
public class DeviceRebindRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 申请号，给人看、也进飞书卡片 */
    private String requestNo;

    private Long subscriptionId;

    private Long userId;

    /** 申请时绑定的设备；此前未绑定为 null */
    private Long fromDeviceId;

    /** 申请改绑到的设备，即提交申请那台机器 */
    private Long toDeviceId;

    private String reason;

    private RebindRequestStatus status;

    private Long decidedBy;

    private Instant decidedAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
