package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * node_group 表映射。持久化形态里订阅链接是密文，
 * 业务层一律用 NodeGroupDto（明文），转换由 NodeGroupConverter 负责。
 */
@Data
@TableName("node_group")
public class NodeGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 订阅链接的密文（链接含 token，属凭据） */
    private String subUrlCipher;

    private String remark;

    /** 由数据库默认值维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    /** 由数据库 ON UPDATE 维护，应用永不写入 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
