package ai.mintpop.lane.enumeration;

/**
 * 一条席位相对**本次请求那台设备**的绑定关系。不落库，是每次下发链路配置时算出来的。
 *
 * <p>凭据是否下发跟着它走：只有 {@link #BOUND_HERE} 才带凭据，另两种一律置空串。
 * 这就是「一份订阅只能在一台设备上用」的强制点——客户端拿这三个取值只是为了把原因讲清楚。
 */
public enum DeviceBinding {

    /** 这份订阅还没在任何设备上用过。客户端确认后即可绑到本机 */
    UNBOUND,

    /** 绑在发起本次请求的这台设备上，凭据正常下发 */
    BOUND_HERE,

    /** 绑在别的设备上。本机用不了，只能提换机申请 */
    BOUND_ELSEWHERE
}
