package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.LinkReportDaily;

import java.time.LocalDate;
import java.util.Optional;

/** 链路上报按天聚合（link_report_daily）的读写口。上层只依赖这个接口，看不到 MyBatis-Plus。 */
public interface LinkReportDailyRepository {

    /** 按唯一键 (userId, failureDomain, isp, statDate) 查找已有聚合行；不存在时返回空 */
    Optional<LinkReportDaily> find(Long userId, String failureDomain, String isp, LocalDate statDate);

    /**
     * 按唯一键 (user_id, failure_domain, isp, stat_date) 幂等写入：命中则<b>整行覆盖</b>，不是累加。
     * {@code isp} 必须已由调用方把 null 转换成空串——本表的 isp 是 NOT NULL DEFAULT ''。
     * 调用方负责算好最终总数（含与已有行相加），本方法只落库。
     */
    void upsertDay(LinkReportDaily row);

    /** 删除统计日早于 {@code before} 的全部按天聚合行，供归档任务清理过保留期的数据 */
    void deleteBefore(LocalDate before);
}
