package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.mapper.LinkReportDailyMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 链路上报按天聚合的 MySQL 实现。 */
@Repository
public class MybatisLinkReportDailyRepository implements LinkReportDailyRepository {

    private final LinkReportDailyMapper mapper;

    public MybatisLinkReportDailyRepository(LinkReportDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LinkReportDaily> find(Long userId, String failureDomain, String asn, LocalDate statDate) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<LinkReportDaily>lambdaQuery()
                .eq(LinkReportDaily::getUserId, userId)
                .eq(LinkReportDaily::getFailureDomain, failureDomain)
                .eq(LinkReportDaily::getAsn, asn)
                .eq(LinkReportDaily::getStatDate, statDate)));
    }

    @Override
    public void upsertDay(LinkReportDaily row) {
        mapper.upsertDay(row);
    }

    @Override
    public void deleteBefore(LocalDate before) {
        mapper.delete(Wrappers.<LinkReportDaily>lambdaQuery()
                .lt(LinkReportDaily::getStatDate, before));
    }

    @Override
    public List<LinkReportRepository.DomainAsnAggregate> aggregateGlobalByDomainAndAsn(LocalDate from, LocalDate to) {
        return mapper.selectGlobalGroupedByDomainAndAsn(from, to).stream()
                // 本表 asn 是 NOT NULL DEFAULT ''，空串表示反查失败；归一化成 null，
                // 与 link_report.source_asn 的 null 编码对齐，调用方合并两表结果时不用记两套判断条件
                .map(row -> new LinkReportRepository.DomainAsnAggregate(row.getFailureDomain(),
                        row.getAsn() == null || row.getAsn().isEmpty() ? null : row.getAsn(),
                        row.getSamples(), row.getAliveCount(), row.getFailovers()))
                .toList();
    }
}
