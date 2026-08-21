package ai.mintpop.lane.enumeration;

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
    TICKET_INVALID(210004, "登录票据无效或已过期，请重新登录"),

    /* 链路 */
    EGRESS_NOT_ASSIGNED(310001, "尚未为该用户分配落地出口"),
    // 310002 曾是 CREDENTIAL_NOT_ASSIGNED（凭据未录入拦建链），已解耦：凭据只影响会话，不再拦链路。号位不复用
    LINK_REVOKED(310003, "该用户的链路已被吊销"),
    NODE_DISABLED(310004, "链路节点已被禁用"),
    SERVICE_NOT_PURCHASED(310005, "尚未购买任何服务"),
    SERVICE_EXPIRED(310006, "服务已到期，请续费"),

    /* 管理端 */
    NODE_NOT_FOUND(410001, "节点不存在"),
    LAND_NODE_OCCUPIED(410002, "该落地节点已被其他用户占用"),
    NODE_IN_USE(410003, "该节点仍被用户引用，无法删除"),
    NODE_ROLE_MISMATCH(410005, "节点角色与用途不符"),
    USER_NOT_FOUND(410006, "用户不存在"),
    NODE_NAME_DUPLICATED(410007, "节点名已存在"),
    SUBSCRIPTION_NOT_FOUND(410008, "订阅不存在"),
    NODE_GROUP_NOT_FOUND(410009, "分组不存在"),
    NODE_GROUP_NAME_DUPLICATED(410010, "分组名已存在"),
    SUB_FETCH_FAILED(410011, "订阅拉取失败：链接无法访问或返回错误"),
    SUB_PARSE_FAILED(410012, "订阅解析失败：不是可识别的 Clash YAML 或没有有效节点"),
    NODE_GROUP_IN_USE(410013, "分组内有节点被用户引用，无法删除"),
    SELECTED_NODE_MISSING(410014, "勾选的节点在订阅中已不存在，请重新预览");

    private final int code;
    private final String message;
}
