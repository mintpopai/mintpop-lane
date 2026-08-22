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

    /**
     * 出口 IP，仅 LAND 有值；NULL 表示未填。
     * updateStrategy = ALWAYS：MyBatis-Plus 默认跳过 null 字段，
     * 没有它「把出口 IP 清空」的更新会被静默忽略。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String egressIp;

    /**
     * 落地出口 IP 对应的 IANA 时区名，仅 LAND 有值；NULL 表示未填。
     * updateStrategy 同 egressIp：不加 ALWAYS，「清空时区」的更新会被静默忽略。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String egressTimezone;

    private NodeStatus status;

    private String remark;

    /** 所属分组 id；NULL 表示手工节点 */
    private Long groupId;

    /** 订阅里的原始节点名，重新拉取时据此匹配；手工节点为 NULL */
    private String sourceName;

    /** 订阅节点的真实 mihomo type（如 anytls），仅供展示；手工节点为 NULL */
    private String sourceType;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 由数据库 ON UPDATE 维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
