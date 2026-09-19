package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.LinkReportDto;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** link_report 表的 SQL 层。查询/删除由 BaseMapper 提供，upsert 需要 ON DUPLICATE KEY UPDATE，单写一条。 */
@Mapper
public interface LinkReportMapper extends BaseMapper<LinkReportDto> {

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
    int upsertWindow(LinkReportDto report);
}
