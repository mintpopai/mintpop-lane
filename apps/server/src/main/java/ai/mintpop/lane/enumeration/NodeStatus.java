package ai.mintpop.lane.enumeration;

/** 节点状态。 */
public enum NodeStatus {

    /** 可分配、可下发 */
    ENABLED,

    /** 禁用：不可分配给用户，已绑定的用户也拿不到链路 */
    DISABLED
}
