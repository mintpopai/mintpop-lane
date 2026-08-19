package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 用户的明文领域对象。席位凭据在这里是明文，密文只存在于 entity 与 converter 之间。
 */
@Data
public class UserDto {

    private Long id;

    /** Logto 中的 user id，即 JWT 的 sub */
    private String subject;

    private String name;

    private UserRole role = UserRole.MEMBER;

    private UserStatus status = UserStatus.ACTIVE;

    /** 第一跳节点 id */
    private Long frontNodeId;

    /** 第二跳落地节点 id，null 表示尚未分配 */
    private Long landNodeId;

    /** Claude 席位长效凭据（明文）。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private String claudeCredential;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
