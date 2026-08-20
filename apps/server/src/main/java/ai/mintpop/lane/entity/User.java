package ai.mintpop.lane.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import lombok.Data;

import java.time.Instant;

/**
 * app_user 表映射。这是「带密文」的持久化形态，
 * 业务层一律使用 UserDto（明文），转换由 UserConverter 负责。
 */
@Data
@TableName("app_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Logto 中的 user id，即 JWT 的 sub */
    private String subject;

    /** 邮箱，登录时从 id_token 同步 */
    private String email;

    private String name;

    private UserRole role;

    private UserStatus status;

    private Long frontNodeId;

    /** 落地节点 id，null 表示尚未分配 */
    private Long landNodeId;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Instant updatedAt;
}
