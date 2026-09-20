package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.LinkReportDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * link_report_daily 表的 SQL 层。查询/删除由 BaseMapper 提供，upsert 需要
 * ON DUPLICATE KEY UPDATE，单写一条。
 */
@Mapper
public interface LinkReportDailyMapper extends BaseMapper<LinkReportDaily> {

    /**
     * 按唯一键 (user_id, failure_domain, isp, stat_date) 幂等写入：命中则整行覆盖，不累加。
     * 调用方（{@code LinkReportArchiveService}）已经把「已有聚合行 + 本轮批次」求好和，
     * 传进来的就是这一维度的最终总数，这里只负责整行覆盖落库，不做任何算术。
     */
    @Insert("""
            INSERT INTO link_report_daily
                (user_id, failure_domain, isp, stat_date, samples, alive_count, no_sample_count, failovers)
            VALUES
                (#{userId}, #{failureDomain}, #{isp}, #{statDate}, #{samples}, #{aliveCount},
                 #{noSampleCount}, #{failovers})
            ON DUPLICATE KEY UPDATE
                samples = VALUES(samples),
                alive_count = VALUES(alive_count),
                no_sample_count = VALUES(no_sample_count),
                failovers = VALUES(failovers)
            """)
    int upsertDay(LinkReportDaily row);

    /**
     * 全库范围「故障域 × 运营商」聚合（按统计日区间），供管理端链路健康矩阵覆盖超过原始保留期
     * 的历史。{@code isp} 是本表 {@code NOT NULL DEFAULT ''} 的编码（空串表示反查失败），
     * 与 {@code link_report} 的 null 编码不同——归一化交给 Repository 层。
     */
    @Select("""
            SELECT failure_domain, isp,
                   SUM(samples) AS samples, SUM(alive_count) AS alive_count, SUM(failovers) AS failovers
            FROM link_report_daily
            WHERE stat_date >= #{from} AND stat_date <= #{to}
            GROUP BY failure_domain, isp
            """)
    List<LinkReportDomainIspAggregateRow> selectGlobalGroupedByDomainAndIsp(@Param("from") LocalDate from,
                                                                             @Param("to") LocalDate to);
}
