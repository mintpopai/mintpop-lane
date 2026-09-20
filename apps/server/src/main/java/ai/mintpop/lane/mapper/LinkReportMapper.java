package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.LinkReport;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/** link_report 表的 SQL 层。查询/删除由 BaseMapper 提供，upsert 需要 ON DUPLICATE KEY UPDATE，单写一条。 */
@Mapper
public interface LinkReportMapper extends BaseMapper<LinkReport> {

    /**
     * 按唯一键 (user_id, failure_domain, window_start) 幂等写入：命中则整行覆盖，不累加。
     * 同一窗口重复上报是客户端重试，是同一份数据的重复投递，不是两段新数据，
     * 因此这里覆盖 samples/alive_count/no_sample_count/failovers/p50_latency_ms/
     * resolved_entry_ip/source_asn/isp 全部八个可变列。
     */
    @Insert("""
            INSERT INTO link_report
                (user_id, failure_domain, window_start, samples, alive_count, no_sample_count,
                 failovers, p50_latency_ms, resolved_entry_ip, source_asn, isp)
            VALUES
                (#{userId}, #{failureDomain}, #{windowStart}, #{samples}, #{aliveCount}, #{noSampleCount},
                 #{failovers}, #{p50LatencyMs}, #{resolvedEntryIp}, #{sourceAsn}, #{isp})
            ON DUPLICATE KEY UPDATE
                samples = VALUES(samples),
                alive_count = VALUES(alive_count),
                no_sample_count = VALUES(no_sample_count),
                failovers = VALUES(failovers),
                p50_latency_ms = VALUES(p50_latency_ms),
                resolved_entry_ip = VALUES(resolved_entry_ip),
                source_asn = VALUES(source_asn),
                isp = VALUES(isp)
            """)
    int upsertWindow(LinkReport report);

    /**
     * 全库范围「用户 × 故障域 × 运营商」聚合：定时告警要扫全部用户，若照单用户查询那样
     * （取原始行、Java 侧分组求和）逐用户跑一遍，会是 N+1 加全表进内存，因此这里直接在 SQL 层
     * GROUP BY，一次查出全部用户的分组结果。
     */
    @Select("""
            SELECT user_id, failure_domain, isp,
                   SUM(samples) AS samples, SUM(alive_count) AS alive_count, SUM(failovers) AS failovers
            FROM link_report
            WHERE window_start >= #{from} AND window_start < #{to}
            GROUP BY user_id, failure_domain, isp
            """)
    List<LinkReportUserAggregateRow> selectAllUsersGroupedByDomainAndIsp(@Param("from") Instant from,
                                                                          @Param("to") Instant to);

    /**
     * 全库范围「故障域 × 运营商」聚合，连用户维度也在 SQL 层求和掉：管理端链路健康矩阵
     * 没有用户维度（spec §8.3：按「故障域 × 运营商」展示，不按节点也不按用户），
     * 照单用户查询那样取原始行再在 Java 侧分组求和会把全库全部用户的原始窗口行拉进内存。
     */
    @Select("""
            SELECT failure_domain, isp,
                   SUM(samples) AS samples, SUM(alive_count) AS alive_count, SUM(failovers) AS failovers
            FROM link_report
            WHERE window_start >= #{from} AND window_start < #{to}
            GROUP BY failure_domain, isp
            """)
    List<LinkReportDomainIspAggregateRow> selectGlobalGroupedByDomainAndIsp(@Param("from") Instant from,
                                                                             @Param("to") Instant to);
}
