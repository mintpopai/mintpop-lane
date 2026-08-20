package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * proxy_node 表映射。注意这是「带密文」的持久化形态，
 * 业务层一律使用 ProxyNodeDto（明文），转换由 ProxyNodeConverter 负责。
 *
 * autoResultMap = true 是让 JSON 列的 typeHandler 在查询时也生效的前提。
 */
@Data
@TableName(value = "proxy_node", autoResultMap = true)
public class ProxyNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private NodeRole role;

    private NodeProtocol protocol;

    private String serverAddr;

    private Integer port;

    /** 非敏感透传键 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraConfig;

    /** 敏感键 JSON 的密文 */
    private String secretCipher;

    /** 出口 IP 集合，仅 LAND 有值 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> egressIps;

    private NodeStatus status;

    private String remark;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 由数据库 ON UPDATE 维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
