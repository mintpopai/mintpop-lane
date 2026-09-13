package ai.mintpop.lane.response;

/**
 * 落地节点连通性检测结果。
 * 「不通」也是一份正常结果（reachable=false + error），不是请求错误——检测本身就是为了发现不通。
 */
public record NodeProbeResponse(
        /** 经该节点能否访问公网 */
        boolean reachable,
        /** 探测耗时（毫秒）；不通时是失败前耗掉的时间 */
        long latencyMs,
        /** 探测到的实际出口 IP；不通时为 null */
        String actualEgressIp,
        /** 节点上登记的出口 IP；未填为 null */
        String registeredEgressIp,
        /** 实际与登记是否一致；任一侧缺失（未登记 / 不通）时为 null */
        Boolean matched,
        /** 不通的原因摘要；连通时为 null */
        String error
) {
}
