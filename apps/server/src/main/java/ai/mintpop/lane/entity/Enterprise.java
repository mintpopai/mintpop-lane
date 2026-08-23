package ai.mintpop.lane.entity;

import ai.mintpop.lane.enumeration.AgentType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * enterprise 表映射。企业无密文字段，不需要 DTO 形态，直接以实体承载业务数据。
 *
 * autoResultMap = true 是让 agent_types 这个 JSON 列的 typeHandler 在查询时也生效的前提。
 */
@Data
@TableName(value = "enterprise", autoResultMap = true)
public class Enterprise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 企业域名，一律小写存储 */
    private String domain;

    /** 本企业支持的 agent 类型；分配订阅时按它过滤可选套餐 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AgentType> agentTypes;

    /** 启用状态：false 表示停用但保留，停用后不能再分配新订阅 */
    private Boolean enabled;

    private String remark;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
