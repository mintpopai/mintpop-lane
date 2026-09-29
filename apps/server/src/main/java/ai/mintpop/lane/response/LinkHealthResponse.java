package ai.mintpop.lane.response;

import java.util.List;

/**
 * 管理端「链路健康」查询结果：故障域 × 运营商成功率矩阵（spec §8.3）。
 * 矩阵覆盖 {@code link_report}（保留期内的原始窗口）与 {@code link_report_daily}（更早的按天
 * 聚合）两张表，全库跨全部用户求和——三期的分析维度只到故障域与运营商，不按节点也不按用户。
 */
public record LinkHealthResponse(
        /** 故障域 × 运营商的成功率矩阵，按故障域分组 */
        List<DomainRow> domains) {

    /**
     * 一个故障域下按运营商展开的一行。{@code samples}/{@code aliveCount}/{@code failovers}
     * 是该故障域下全部运营商（含未知运营商）之和，不是"跨运营商再算一次成功率"——是否需要域级
     * 成功率、如何从 {@code asns} 里派生，交给前端。
     * <p>
     * {@code failureDomain} 为空串表示"尚未解析出故障域"（与 {@code link_report} 的存储编码
     * 一致），前端要显式显示成「未解析」，不能显示成空白——故障域未解析意味着这组节点的冗余情况
     * 是未知的，比"只有一个故障域"更糟。
     */
    public record DomainRow(String failureDomain, long samples, long aliveCount,
                            long failovers, List<AsnCell> asns) {
    }

    /**
     * 一个"故障域 × 运营商"格子。运营商维度的<b>键是 ASN</b>（形如 AS4134），展示名只是标签：
     * 上游对同一个 ASN 的文案会漂（今天 China Telecom、明天 CHINANET-BACKBONE），拿名字做键
     * 会把同一家运营商裂成两列。
     * <p>
     * 这里有<b>三种含义完全不同的"空"</b>，前端必须分开处理，不能笼统当成"没值"：
     * <ul>
     *   <li>{@code asn} 为<b>空串</b>：这一组样本的 ASN 反查全部失败，连是谁都不知道。两张底表对
     *       "反查失败"的编码并不相同——{@code link_report.source_asn} 是 null 编码，
     *       {@code link_report_daily.asn} 是 {@code NOT NULL DEFAULT ''} 编码——查询时已经在服务层
     *       把两者统一归一成空串再合并求和，所以同一个"未知运营商"只会在矩阵上出现一行，
     *       这里不会再看到 null；</li>
     *   <li>{@code orgName} 为 <b>null</b>：ASN 是知道的，只是 {@code asn_org} 里还没见过它的展示名
     *       （上游当时没给名字，或那次旁路写入失败）。此时前端退回显示 ASN 串本身，<b>绝不能因此
     *       把整列藏掉</b>——少一列等于凭空丢掉一批真实流量，比显示一串 AS 号糟得多。空串 {@code asn}
     *       的 {@code orgName} 恒为 null（谁都不知道是哪家，自然没有名字）；</li>
     *   <li>{@code successRate} 为 <b>null</b>：{@code samples} 为 0，不是 {@code 0.0}——
     *       "没有数据"与"全挂"是两回事：把没有样本的格子画成 0% 会让人以为某个运营商彻底不通，
     *       实际只是这段时间没人从那个运营商上来。</li>
     * </ul>
     */
    public record AsnCell(String asn, String orgName, long samples, long aliveCount, Double successRate) {
    }

}
