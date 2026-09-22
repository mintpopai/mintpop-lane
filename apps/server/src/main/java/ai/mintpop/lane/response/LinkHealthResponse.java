package ai.mintpop.lane.response;

import java.time.Instant;
import java.util.List;

/**
 * 管理端「链路健康」查询结果：故障域 × 运营商成功率矩阵 + 入口 IP 变更时间线（spec §8.3）。
 * 矩阵覆盖 {@code link_report}（保留期内的原始窗口）与 {@code link_report_daily}（更早的按天
 * 聚合）两张表，全库跨全部用户求和——三期的分析维度只到故障域与运营商，不按节点也不按用户。
 */
public record LinkHealthResponse(
        /** 故障域 × 运营商的成功率矩阵，按故障域分组 */
        List<DomainRow> domains,
        /** 入口 IP 变更时间线，取自一期的 entry_ip_history，按变更时刻倒序（最近的在前） */
        List<EntryIpChange> entryIpTimeline) {

    /**
     * 一个故障域下按运营商展开的一行。{@code samples}/{@code aliveCount}/{@code failovers}
     * 是该故障域下全部运营商（含未知运营商）之和，不是"跨运营商再算一次成功率"——是否需要域级
     * 成功率、如何从 {@code isps} 里派生，交给前端。
     * <p>
     * {@code failureDomain} 为空串表示"尚未解析出故障域"（与 {@code link_report} 的存储编码
     * 一致），前端要显式显示成「未解析」，不能显示成空白——故障域未解析意味着这组节点的冗余情况
     * 是未知的，比"只有一个故障域"更糟。
     */
    public record DomainRow(String failureDomain, long samples, long aliveCount,
                            long failovers, List<IspCell> isps) {
    }

    /**
     * 一个"故障域 × 运营商"格子。
     * <p>
     * {@code isp} 为空串表示"这一组样本的 ASN 反查全部失败，运营商未知"。两张底表对"反查失败"
     * 的编码并不相同——{@code link_report.source_asn} 是 null 编码，{@code link_report_daily.asn} 是
     * {@code NOT NULL DEFAULT ''} 编码——查询时已经在服务层把两者统一归一成空串再合并求和，
     * 所以同一个"未知运营商"只会在矩阵上出现一行，这里不会再看到 null。
     * <p>
     * {@code successRate} 在 {@code samples} 为 0 时是 {@code null}，不是 {@code 0.0}——
     * "没有数据"与"全挂"是两回事：把没有样本的格子画成 0% 会让人以为某个运营商彻底不通，
     * 实际只是这段时间没人从那个运营商上来。
     */
    public record IspCell(String isp, long samples, long aliveCount, Double successRate) {
    }

    /**
     * 一次入口 IP 变更事件。{@code vantage} 是 {@code DnsVantage} 枚举的 {@code name()}
     * （如 {@code CHINA_TELECOM}）。{@code previousIps}/{@code currentIps} 是变更前后的入口 IP
     * 列表（逗号分隔，字典序），{@code changedAt} 是变更后那次观测的时刻——即
     * {@code entry_ip_history} 里"值变化的那一条"的 {@code observed_at}，不是变更前那条的。
     */
    public record EntryIpChange(String failureDomain, String vantage,
                                String previousIps, String currentIps, Instant changedAt) {
    }
}
