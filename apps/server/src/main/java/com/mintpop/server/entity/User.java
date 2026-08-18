package com.mintpop.server.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mintpop.server.enumeration.UserRole;
import com.mintpop.server.enumeration.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

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

    private String name;

    private UserRole role;

    private UserStatus status;

    private Long frontNodeId;

    /** 落地节点 id，null 表示尚未分配 */
    private Long landNodeId;

    /** Claude 席位长效凭据的密文 */
    private String claudeCredentialCipher;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
