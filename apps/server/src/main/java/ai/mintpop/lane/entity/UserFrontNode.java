package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * user_front_node 表映射：用户可用的前置节点集合，按故障域分散下发，
 * 供客户端在其中组两层 fallback。app_user.front_node_id 仍保留，
 * 语义收窄为「主前置节点」＝本集合里的首选，也是老客户端唯一能理解的那一个。
 */
@Data
@TableName("user_front_node")
public class UserFrontNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id，引用 app_user */
    private Long userId;

    /** 前置节点 id，引用 proxy_node */
    private Long nodeId;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
}
