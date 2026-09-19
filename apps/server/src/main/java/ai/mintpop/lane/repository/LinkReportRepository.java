package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.LinkReport;

import java.time.Instant;
import java.util.List;

/**
 * 链路上报窗口聚合（link_report）的读写口。上层只依赖这个接口，看不到 MyBatis-Plus。
 */
public interface LinkReportRepository {

    /**
     * 按唯一键 (user_id, failure_domain, window_start) 幂等写入：命中则<b>整行覆盖</b>，不是累加。
     * 同一窗口重复上报是客户端重试投递的同一份数据，不是两段新窗口。
     */
    void upsertWindow(LinkReport report);

    /** 窗口起点早于 {@code before} 的全部原始窗口，供归档任务读出后压成按天聚合 */
    List<LinkReport> findWindowsBefore(Instant before);

    /** 删除窗口起点早于 {@code before} 的全部原始窗口，供归档任务在压完当天聚合后清理 */
    void deleteWindowsBefore(Instant before);

    /**
     * 该用户在 [{@code from}, {@code to}) 区间内，按「故障域 × 运营商」分组聚合的成功率原始数据
     * （samples/aliveCount/failovers 逐组求和）。三期分析维度只到故障域与运营商，不按节点——
     * 同一故障域下的节点共用一台中转入口机，不是独立样本。
     */
    List<DomainIspAggregate> aggregateByDomainAndIsp(Long userId, Instant from, Instant to);

    /**
     * 一行「故障域 × 运营商」的聚合结果。{@code isp} 为 {@code null} 表示这些窗口的 ASN 反查失败，
     * 与 {@link LinkReport#getIsp()} 同一语义。这是只读投影，不对应任何表。
     */
    record DomainIspAggregate(String failureDomain, String isp, long samples, long aliveCount, long failovers) {
    }
}
