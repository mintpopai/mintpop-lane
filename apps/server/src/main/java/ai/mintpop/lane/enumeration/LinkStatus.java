package ai.mintpop.lane.enumeration;

/**
 * 心跳返回给客户端的链路状态：账号处置态的对客视图，客户端据此决定断链方式。
 * 订阅在期与否不参与判定——套餐只影响席位，不拦网络链路。
 * 曾有 EXPIRED（在期订阅归零即断链），随套餐与网络解耦移除，取值名不复用。
 */
public enum LinkStatus {

    /** 正常：处置态 ACTIVE */
    ACTIVE,

    /** 账号被临时停用：断链并回登录页 */
    SUSPENDED,

    /** 账号被吊销（或已被删除）：断链并回登录页 */
    REVOKED
}
