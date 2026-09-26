package ai.mintpop.lane.mapper;

import lombok.Data;

/**
 * {@link LinkReportMapper#selectAllUsersGroupedByDomainAndAsn} 的行映射，只用于 Mapper 到
 * Repository 之间的搬运，不对外暴露——Repository 转换成
 * {@link ai.mintpop.lane.repository.LinkReportRepository.UserDomainAsnAggregate} 供上层使用。
 * 字段名与 SQL 聚合列一一对应，`map-underscore-to-camel-case` 已全局开启。
 */
@Data
public class LinkReportUserAggregateRow {

    private Long userId;
    private String failureDomain;
    /** 运营商维度的键：ASN（形如 AS4134），由 source_asn 取别名而来 */
    private String asn;
    private Long samples;
    private Long aliveCount;
    private Long failovers;
}
