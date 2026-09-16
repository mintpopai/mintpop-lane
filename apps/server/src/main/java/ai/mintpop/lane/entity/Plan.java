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

    /**
     * 面向用户的短描述，控制台购买卡片的副标题，可空。
     * 更新策略设为 ALWAYS：{@link ai.mintpop.lane.request.PlanSaveRequest} 的契约是
     * 「更新时全量覆盖」，默认的 NOT_NULL 策略会导致清空该字段的更新被静默跳过。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;

    /** 套餐图公开 URL，可空。更新策略同 description，见其注释 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String imageUrl;

    /** 套餐详情富文本，已净化的 HTML，可空。更新策略同 description，见其注释 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String detail;

    /** 上架状态：false 表示停用但保留 */
    private Boolean enabled;

    /**
     * 管理员自用备注，可空。更新策略同 description：契约是全量覆盖，不能寄望于
     * 「前端永远会发一个 ""」这种调用方习惯来保证清空生效。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 由数据库 ON UPDATE 维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
