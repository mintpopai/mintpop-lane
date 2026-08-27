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
    EMAIL_ALREADY_BOUND(210005, "该邮箱已绑定其它 Logto 账号"),

    /* 链路 */
    EGRESS_NOT_ASSIGNED(310001, "尚未为该用户分配落地出口"),
    // 310002 曾是 CREDENTIAL_NOT_ASSIGNED（凭据未录入拦建链），已解耦：凭据只影响会话，不再拦链路。号位不复用
    LINK_REVOKED(310003, "该用户的链路已被吊销"),
    NODE_DISABLED(310004, "链路节点已被禁用"),
    // 310005 曾是 SERVICE_NOT_PURCHASED、310006 曾是 SERVICE_EXPIRED（套餐拦建链），
    // 已解耦：套餐只影响席位凭据，不再拦链路。号位不复用

    /* 管理端 */
    NODE_NOT_FOUND(410001, "节点不存在"),
    // 410002 曾是 LAND_NODE_OCCUPIED（一人一座时代），已改为容量制（见 410016）。号位不复用
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
    SELECTED_NODE_MISSING(410014, "勾选的节点在订阅中已不存在，请重新预览"),
    NODE_TIMEZONE_INVALID(410015, "出口时区不是合法的 IANA 时区名"),
    LAND_NODE_FULL(410016, "该落地节点容量已满，无法再分配"),
    PLAN_NOT_FOUND(410017, "套餐不存在"),
    PLAN_NAME_DUPLICATED(410018, "套餐名已存在"),
    PLAN_DISABLED(410019, "套餐已停用"),
    ENTERPRISE_NOT_FOUND(410020, "企业不存在"),
    ENTERPRISE_NAME_DUPLICATED(410021, "企业名称已存在"),
    ENTERPRISE_DOMAIN_DUPLICATED(410022, "企业域名已存在"),
    ENTERPRISE_DISABLED(410023, "企业已停用，无法分配订阅"),
    ENTERPRISE_AGENT_TYPE_MISMATCH(410024, "该企业不支持此套餐的 agent 类型"),
    ENTERPRISE_IN_USE(410025, "该企业仍被订阅引用，无法删除"),
    SUBSCRIPTION_ACCOUNT_DOMAIN_MISMATCH(410026, "账号邮箱域名与归属企业域名不一致"),
    NODE_PROTOCOL_NOT_ALLOWED(410027, "该协议不能用于此角色的节点"),
    EGRESS_IP_MISMATCH(410028, "落地节点实际出口与登记的出口 IP 不一致，请核对节点配置"),
    EGRESS_PROBE_FAILED(410029, "落地节点出口探测失败，请确认该节点当前可用后重试"),
    CREDENTIAL_EXCHANGE_FAILED(410030, "凭证兑换失败，请确认授权码正确且未过期"),
    CREDENTIAL_REVOKE_FAILED(410031, "凭证吊销失败"),
    LINK_NOT_READY_FOR_ISSUE(410032, "该用户链路尚未配置完整，无法签发凭证"),
    CREDENTIAL_ISSUE_NOT_SUPPORTED(410033, "该席位类型不支持凭证签发"),
    CREDENTIAL_SCOPE_INSUFFICIENT(410034, "服务端授予的权限不足，签发中止"),
    CREDENTIAL_LIFETIME_TRUNCATED(410035, "服务端签发的凭证有效期远短于请求值，签发中止"),
    OAUTH_SESSION_INVALID(410036, "授权会话不存在或已过期，请重新发起签发"),
    CREDENTIAL_MANUAL_NOT_ALLOWED(410037, "Claude 席位的凭证只能通过签发获得，不支持手工录入");

    private final int code;
    private final String message;
}
