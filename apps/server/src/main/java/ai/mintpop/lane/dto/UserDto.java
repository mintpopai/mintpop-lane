package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import lombok.Data;

import java.time.Instant;

/**
 * 用户的明文领域对象：身份、链路绑定与账号处置态。
 * 服务权益（凭据在内）已搬到 SubscriptionDto，这里不再有密文字段。
 */
@Data
public class UserDto {

    private Long id;

    /** Logto 中的 user id，即 JWT 的 sub；登录查档键 */
    private String subject;

    /** 邮箱，登录时从 id_token 同步；用户的唯一业务标识 */
    private String email;

    private UserRole role = UserRole.MEMBER;

    private UserStatus status = UserStatus.ACTIVE;

    /** 第一跳节点 id */
    private Long frontNodeId;

    /** 第二跳落地节点 id，null 表示尚未分配 */
    private Long landNodeId;

    private Instant createdAt;

    private Instant updatedAt;
}
