package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.DnsVantage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        /** 与库中已有节点撞故障域的分组名；非空即应否决这次采购 */
        List<String> conflictsWith,
        /** 订阅里出现过的 mihomo type 集合，用于确认 front-tuning 覆盖表是否已支持 */
        List<String> protocols,
        Long usedBytes, Long totalBytes, Instant expiresAt
) {
    /**
     * 一个故障域的尽调结论。
     * <p>
     * {@code entryIps} / {@code asns} / {@code lineSplit} **三个字段一起为 null 表示「本次未查询」**：
     * 入口 IP 与 ASN 只对判定为美国落地的故障域查（见 {@code SubAuditServiceImpl}），
     * 港日故障域用不上、查了只是白白放大外呼扇出。留 null 而不是空表/false，是为了让页面能说
     * 「未查询入口 IP」——空表会被读成「查了但没结果」，false 会被读成「查了，没分线路」，都是误导。
     */
    public record FailureDomainReport(
            String domain,
            int nodeCount,
            int usNodeCount,
            /** 各视角解析到的入口 IP；null 表示本次未查询该故障域 */
            Map<DnsVantage, List<String>> entryIps,
            /** 入口 IP 对应的 ASN，反查失败为空；null 表示本次未查询该故障域 */
            Map<DnsVantage, List<String>> asns,
            /** 各视角是否解析到不同 IP——有分线路者国内优化更好；null 表示本次未查询该故障域 */
            Boolean lineSplit
    ) {}
}
