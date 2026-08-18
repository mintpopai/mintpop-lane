package com.mintpop.server.enumeration;

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
    LINK_REVOKED(310003, "该用户的链路已被吊销");

    private final int code;
    private final String message;
}
