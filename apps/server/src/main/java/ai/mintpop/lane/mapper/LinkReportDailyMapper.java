package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.LinkReportDaily;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
}
