package ai.mintpop.lane.enumeration;

/**
 * 心跳返回给客户端的链路状态。与 UserStatus（账号处置态）不同，
 * 这是「处置态 + 订阅在期与否」合成后的对客视图，客户端据此决定断链方式。
 */
public enum LinkStatus {

    /** 正常：处置态 ACTIVE 且存在在期订阅 */
    ACTIVE,

    /** 账号被临时停用：断链并回登录页 */
    SUSPENDED,

    /** 账号被吊销（或已被删除）：断链并回登录页 */
    REVOKED,

    /** 账号正常但在期订阅归零（使用中跨过止期）：断链但保留登录态，提示续费 */
    EXPIRED
}
