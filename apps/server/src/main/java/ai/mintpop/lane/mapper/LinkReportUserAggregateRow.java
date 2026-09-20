package ai.mintpop.lane.mapper;

import lombok.Data;

/**
 * {@link LinkReportMapper#selectAllUsersGroupedByDomainAndIsp} 的行映射，只用于 Mapper 到
 * Repository 之间的搬运，不对外暴露——Repository 转换成
 * {@link ai.mintpop.lane.repository.LinkReportRepository.UserDomainIspAggregate} 供上层使用。
 * 字段名与 SQL 聚合列一一对应，`map-underscore-to-camel-case` 已全局开启。
 */
@Data
public class LinkReportUserAggregateRow {

    private Long userId;
    private String failureDomain;
    private String isp;
    private Long samples;
    private Long aliveCount;
    private Long failovers;
}
