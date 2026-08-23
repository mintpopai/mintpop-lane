package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * plan 表映射。全部字段都是明文，没有密文形态，
 * 因此不设 DTO 层，业务层直接使用本实体。
 */
@Data
@TableName("plan")
public class Plan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 本套餐面向的 agent 类型 */
    private AgentType agentType;

    /** 套餐时长（天），正整数 */
    private Integer durationDays;

    private BigDecimal price;

    private Currency currency;

    /** 上架状态：false 表示停用但保留 */
    private Boolean enabled;

    private String remark;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 由数据库 ON UPDATE 维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
