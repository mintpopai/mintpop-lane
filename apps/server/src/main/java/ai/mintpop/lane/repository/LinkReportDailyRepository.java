package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.repository.LinkReportRepository.DomainAsnAggregate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 链路上报按天聚合（link_report_daily）的读写口。上层只依赖这个接口，看不到 MyBatis-Plus。 */
public interface LinkReportDailyRepository {

    /** 按唯一键 (userId, failureDomain, asn, statDate) 查找已有聚合行；不存在时返回空 */
    Optional<LinkReportDaily> find(Long userId, String failureDomain, String asn, LocalDate statDate);

    /**
     * 按唯一键 (user_id, failure_domain, asn, stat_date) 幂等写入：命中则<b>整行覆盖</b>，不是累加。
     * {@code asn} 必须已由调用方把 null 转换成空串——本表的 asn 是 NOT NULL DEFAULT ''。
     * 调用方负责算好最终总数（含与已有行相加），本方法只落库。
     */
    void upsertDay(LinkReportDaily row);

    /** 删除统计日早于 {@code before} 的全部按天聚合行，供归档任务清理过保留期的数据 */
    void deleteBefore(LocalDate before);

    /**
     * 全库范围「故障域 × ASN」聚合（统计日闭区间 [{@code from}, {@code to}]），SQL 层 GROUP BY，
     * 供管理端链路健康矩阵覆盖超过原始保留期（{@code link_report} 只保留 7 天）的历史。
     * <p>
     * 返回形状复用 {@link LinkReportRepository.DomainAsnAggregate}，但 {@code asn} 的空串已经在
     * 这里被归一化成 {@code null}——本表 {@code asn} 是 {@code NOT NULL DEFAULT ''} 编码（空串表示
     * 反查失败），与 {@code link_report.source_asn} 的 null 编码不同；归一化到 null 让调用方合并
     * 两张表的结果时不用记两套"未知运营商"的判断条件。
     */
    List<DomainAsnAggregate> aggregateGlobalByDomainAndAsn(LocalDate from, LocalDate to);
}
