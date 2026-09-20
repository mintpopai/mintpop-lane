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
     * 全库范围「用户 × 故障域 × 运营商」聚合，SQL 层 GROUP BY——供定时告警扫描全部用户使用。
     * 与 {@link #aggregateByDomainAndIsp} 的区别：那个方法是单用户、取出原始行后 Java 侧分组求和；
     * 定时任务要扫全库，照那样写会是 N+1 加全表进内存，因此单独开一个方法把分组求和收进 SQL。
     */
    List<UserDomainIspAggregate> aggregateAllUsersByDomainAndIsp(Instant from, Instant to);

    /**
     * 全库范围「故障域 × 运营商」聚合，不含用户维度，SQL 层 GROUP BY——供管理端链路健康矩阵使用。
     * 与 {@link #aggregateAllUsersByDomainAndIsp} 的区别：那个还按用户切分；管理端矩阵没有用户维度
     * （spec §8.3：按「故障域 × 运营商」展示，不按节点也不按用户），因此这里连用户也在 SQL 层
     * 求和掉——不能先调 {@link #aggregateAllUsersByDomainAndIsp} 再在 Java 侧把 userId 合并掉，
     * 那样仍会把「分组数 × 用户数」的行拉回内存。
     */
    List<DomainIspAggregate> aggregateGlobalByDomainAndIsp(Instant from, Instant to);

    /**
     * 一行「故障域 × 运营商」的聚合结果。{@code isp} 为 {@code null} 表示这些窗口的 ASN 反查失败，
     * 与 {@link LinkReport#getIsp()} 同一语义。这是只读投影，不对应任何表。
     */
    record DomainIspAggregate(String failureDomain, String isp, long samples, long aliveCount, long failovers) {
    }

    /**
     * 一行「用户 × 故障域 × 运营商」的全库聚合结果，{@code isp} 语义同 {@link DomainIspAggregate}。
     * 这是只读投影，不对应任何表。
     */
    record UserDomainIspAggregate(Long userId, String failureDomain, String isp, long samples, long aliveCount,
                                  long failovers) {
    }
}
