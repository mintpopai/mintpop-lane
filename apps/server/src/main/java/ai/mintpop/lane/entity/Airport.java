package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** airport 表映射。机场没有密文字段，直接以实体承载业务数据。 */
@Data
@TableName("airport")
public class Airport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 机场地址（官网或用户中心），可空。
     * updateStrategy = ALWAYS：MyBatis-Plus 默认跳过 null 字段，
     * 没有它「把地址清空」的更新会被静默忽略。同 {@link ProxyNode#getEgressIp()}、
     * {@link Plan#getDescription()}、{@link AirportSubscription#getTrafficAlertedPct()} 的处理。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String websiteUrl;

    /** 备注，可空。更新策略同 websiteUrl，见其注释 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
