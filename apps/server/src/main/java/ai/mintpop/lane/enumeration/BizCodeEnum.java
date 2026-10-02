package ai.mintpop.lane.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务错误码。6 位分段：前两位为模块号，后四位为段内序号。
 * 11 通用/系统，21 认证与身份，31 链路，41 管理端，51 用户自助。
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
    DEVICE_ID_MISSING(310007, "请求缺少本机标识，请升级客户端后重试"),
    CLIENT_VERSION_OUTDATED(310008, "客户端版本过低，请更新到最新版本"),

    /* 管理端 */
    NODE_NOT_FOUND(410001, "节点不存在"),
    // 410002 曾是 LAND_NODE_OCCUPIED（一人一座时代），已改为容量制（见 410016）。号位不复用
    NODE_IN_USE(410003, "该节点仍被用户引用，无法删除"),
    NODE_ROLE_MISMATCH(410005, "节点角色与用途不符"),
    USER_NOT_FOUND(410006, "用户不存在"),
    NODE_NAME_DUPLICATED(410007, "节点名已存在"),
    SUBSCRIPTION_NOT_FOUND(410008, "订阅不存在"),
    AIRPORT_SUBSCRIPTION_NOT_FOUND(410009, "机场订阅不存在"),
    AIRPORT_SUBSCRIPTION_NAME_DUPLICATED(410010, "订阅名已存在"),
    SUB_FETCH_FAILED(410011, "订阅拉取失败：链接无法访问或返回错误"),
    SUB_PARSE_FAILED(410012, "订阅解析失败：不是可识别的 Clash YAML 或没有有效节点"),
    AIRPORT_SUBSCRIPTION_IN_USE(410013, "订阅仍被用户使用，请先为这些用户重新分配"),
    // 410014 曾是 SELECTED_NODE_MISSING（导入要逐个勾选的时代），改为自动导入美国节点后废弃。号位不复用
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
    CREDENTIAL_MANUAL_NOT_ALLOWED(410037, "Claude 席位的凭证只能通过签发获得，不支持手工录入"),
    CREDENTIAL_NOT_FOUND(410038, "该席位尚未录入凭证，无需吊销"),
    ADMIN_USER_PROTECTED(410039, "管理员账号受保护，不允许停用、吊销或删除"),
    NODE_PROBE_UNSUPPORTED(410040, "只有落地节点支持出口检测，前置节点的协议服务端无法直连"),
    SUBSCRIPTION_NOT_ACTIVATED(410041, "订阅尚未开通，请先填写起期"),
    REBIND_REQUEST_NOT_FOUND(410042, "换机申请不存在"),
    REBIND_REQUEST_NOT_PENDING(410043, "该换机申请已被处理"),
    IMAGE_STORAGE_NOT_CONFIGURED(410044, "图片存储未配置"),
    IMAGE_TOO_LARGE(410045, "图片不能超过 5 MB"),
    IMAGE_TYPE_UNSUPPORTED(410046, "只支持 JPEG / PNG / WebP / GIF 图片"),
    IMAGE_STORAGE_ERROR(410047, "图片存储写入失败，请稍后重试"),
    // 410048 曾是 FRONT_NODE_UNALLOCATABLE（按节点自动分配失败），改为按机场订阅分配后废弃。号位不复用
    // 导入不再逐个勾选、自动取美国节点：一个都没有时必须报错，不建一个空订阅冒充导入成功
    SUB_NO_REGION_NODES(410049, "订阅里没有当前筛选地区的节点，未导入"),
    AIRPORT_NOT_FOUND(410051, "机场不存在"),
    AIRPORT_NAME_DUPLICATED(410052, "机场名已存在"),
    AIRPORT_IN_USE(410053, "机场下还有订阅，无法删除"),
    // planned 为空的两种成因都会报这条：所有订阅主用占满，或压根没有可用的候选订阅（当前地区没有节点）
    FRONT_CAPACITY_FULL(410050, "没有可分配的第一跳订阅：主用机场的名额已满，或当前地区没有节点"),
    SETTING_INVALID(410054, "全局配置取值非法：每人机场数 1 到 10，每人带宽 1 到 1000 Mbps"),
    FRONT_REBUILD_RUNNING(410055, "正在为全部用户重算线路，请等本次完成后再试"),
    FRONT_CAPACITY_INSUFFICIENT(410056, "主用名额不足，无法为全部用户分配线路"),
    FRONT_REBUILD_FETCH_FAILED(410057, "订阅拉取失败，本次重算已中止"),
    FRONT_MANUAL_INVALID(410058, "手动分配的线路不合法"),

    /* 用户自助（控制台） */
    PLAN_NOT_AVAILABLE(510001, "套餐不存在或已下架"),
    ORDER_NOT_FOUND(510002, "订单不存在"),
    ORDER_NOT_PAYABLE(510003, "订单当前不可支付"),
    ORDER_NOT_CANCELLABLE(510004, "订单当前不可取消"),
    PAYMENT_NOT_CONFIGURED(510005, "支付功能未配置"),
    PAYMENT_GATEWAY_ERROR(510006, "支付网关异常，请稍后重试"),
    USER_NOT_ACTIVE(510007, "账号当前不可购买"),
    SUBSCRIPTION_BOUND_ELSEWHERE(510008, "该订阅已绑定到其它设备，请提交换机申请"),
    SUBSCRIPTION_NOT_BOUND_ELSEWHERE(510009, "该订阅未绑定在其它设备上，无需申请换机"),
    // 已过期刻意不复用 410041（订阅尚未开通，请先填写起期）：那句是写给管理员看的待开通提示，
    // 对一份「买过、用过、只是到期了」的订阅说「请先填写起期」会把用户引到完全错误的方向
    SUBSCRIPTION_EXPIRED(510010, "该订阅已过期，请续期后再使用"),
    ORDER_PAYABLE_LIMIT(510011, "未支付的订单过多，请先完成支付或取消后再下单");

    private final int code;
    private final String message;
}
