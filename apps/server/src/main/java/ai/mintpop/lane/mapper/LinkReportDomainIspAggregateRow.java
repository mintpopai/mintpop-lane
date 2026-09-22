package ai.mintpop.lane.mapper;

import lombok.Data;

/**
 * 全库范围「故障域 × ASN」聚合的行映射，不含用户维度——只用于 Mapper 到 Repository 之间的
 * 搬运，供管理端链路健康矩阵使用。{@link LinkReportMapper#selectGlobalGroupedByDomainAndIsp} 与
 * {@link LinkReportDailyMapper#selectGlobalGroupedByDomainAndIsp} 共用同一个行形状，
 * 但两张表的 asn 编码不同（前者 null 表示反查失败，后者空串表示反查失败）——本类只是原样搬运，
 * 不做归一化，归一化在 Repository 转换成
 * {@link ai.mintpop.lane.repository.LinkReportRepository.DomainIspAggregate} 时按各自表的编码处理。
 * 字段名与 SQL 聚合列一一对应，`map-underscore-to-camel-case` 已全局开启。
 */
@Data
public class LinkReportDomainIspAggregateRow {

    private String failureDomain;
    /** 运营商维度的键：ASN（形如 AS4134）。link_report 侧由 source_asn 取别名而来 */
    private String asn;
    private Long samples;
    private Long aliveCount;
    private Long failovers;
}
