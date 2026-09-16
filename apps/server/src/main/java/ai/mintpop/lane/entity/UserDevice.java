package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * user_device 表映射。全部字段明文，没有密文形态，故不设 DTO，业务层直接用本实体。
 */
@Data
@TableName("user_device")
public class UserDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 机器码：客户端对硬件标识做的 SHA-256，小写十六进制定长 64 */
    private String deviceId;

    /** 主机名 */
    private String name;

    /** 系统与版本 */
    private String os;

    /** 机型；客户端取不到时是空串 */
    private String model;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
