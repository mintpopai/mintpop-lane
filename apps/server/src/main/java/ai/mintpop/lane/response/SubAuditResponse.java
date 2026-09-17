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
    public record FailureDomainReport(
            String domain,
            int nodeCount,
            int usNodeCount,
            /** 各视角解析到的入口 IP */
            Map<DnsVantage, List<String>> entryIps,
            /** 入口 IP 对应的 ASN，反查失败为空 */
            Map<DnsVantage, List<String>> asns,
            /** 四个视角是否返回了不同 IP——有分线路者国内优化更好 */
            boolean lineSplit
    ) {}
}
