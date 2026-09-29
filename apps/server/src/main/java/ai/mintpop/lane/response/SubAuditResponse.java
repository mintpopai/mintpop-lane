package ai.mintpop.lane.response;


import java.time.Instant;
import java.util.List;

/**
 * 订阅尽调报告：给一个候选机场的试用订阅链接，判断它是否与库里已有节点撞故障域。
 * 全程只读，不写库；conflictsWith 非空即应否决这次采购。
 */
public record SubAuditResponse(
        /** 机场名，取自 content-disposition 的 filename*；取不到为 null */
        String airportName,
        int totalNodes,
        /** 按名称启发式判定为美国落地的节点数 */
        int usNodeCount,
        /** 判定出的美国节点名，原样列出供人核对——判定是启发式的，不做纯自动决策 */
        List<String> usNodeNames,
        List<FailureDomainReport> failureDomains,
        /** 与库中已有节点撞故障域的订阅名；非空即应否决这次采购 */
        List<String> conflictsWith,
        /** 订阅里出现过的 mihomo type 集合，用于确认 front-tuning 覆盖表是否已支持 */
        List<String> protocols,
        Long usedBytes, Long totalBytes, Instant expiresAt
) {
    /** 一个故障域的尽调结论：故障域名、节点数、其中按名称判定为当前地区落地的节点数 */
    public record FailureDomainReport(String domain, int nodeCount, int usNodeCount) {
    }
}
