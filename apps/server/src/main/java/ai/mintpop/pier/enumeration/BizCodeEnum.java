package ai.mintpop.pier.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码。6 位分段：前两位为模块号，后四位为段内序号。
 * 11 通用/系统，21 认证与身份，31 链路。
 */
@Getter
@AllArgsConstructor
public enum BizCodeEnum {

    /* 通用与系统 */
    PARAM_INVALID(110001, "参数非法"),
    INTERNAL_ERROR(110002, "服务内部错误"),

    /* 认证与身份 */
    TOKEN_INVALID(210001, "令牌无效"),
    TOKEN_EXPIRED(210002, "令牌已过期"),
    ACCOUNT_NOT_ENROLLED(210003, "该账号未开通终端使用权限"),

    /* 链路 */
    EGRESS_NOT_ASSIGNED(310001, "尚未为该用户分配落地出口"),
    CREDENTIAL_NOT_ASSIGNED(310002, "尚未为该用户分配 Claude 席位凭据"),
    LINK_REVOKED(310003, "该用户的链路已被吊销"),
    NODE_DISABLED(310004, "链路节点已被禁用"),

    /* 管理端 */
    NODE_NOT_FOUND(410001, "节点不存在"),
    LAND_NODE_OCCUPIED(410002, "该落地节点已被其他用户占用"),
    NODE_IN_USE(410003, "该节点仍被用户引用，无法删除"),
    USER_ALREADY_EXISTS(410004, "该 Logto 用户已存在"),
    NODE_ROLE_MISMATCH(410005, "节点角色与用途不符"),
    USER_NOT_FOUND(410006, "用户不存在"),
    NODE_NAME_DUPLICATED(410007, "节点名已存在");

    private final int code;
    private final String message;
}
