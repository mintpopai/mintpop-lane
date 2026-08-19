package ai.mintpop.lane.enumeration;

/** 节点在链式代理中承担的角色。 */
public enum NodeRole {

    /** 第一跳：负责出国的机场节点 */
    FRONT,

    /** 第二跳：决定最终出口 IP 的落地代理 */
    LAND
}
