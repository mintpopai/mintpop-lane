package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.mapper.LinkReportMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/** 链路上报窗口聚合的 MySQL 实现。 */
@Repository
public class MybatisLinkReportRepository implements LinkReportRepository {

    private final LinkReportMapper mapper;

    public MybatisLinkReportRepository(LinkReportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsertWindow(LinkReport report) {
        mapper.upsertWindow(report);
    }

    @Override
    public List<LinkReport> findWindowsBefore(Instant before) {
        return mapper.selectList(Wrappers.<LinkReport>lambdaQuery()
                .lt(LinkReport::getWindowStart, before));
    }

    @Override
    public void deleteWindowsBefore(Instant before) {
        mapper.delete(Wrappers.<LinkReport>lambdaQuery()
                .lt(LinkReport::getWindowStart, before));
    }

    @Override
    public List<UserDomainAsnAggregate> aggregateAllUsersByDomainAndAsn(Instant from, Instant to) {
        return mapper.selectAllUsersGroupedByDomainAndAsn(from, to).stream()
                .map(row -> new UserDomainAsnAggregate(row.getUserId(), row.getFailureDomain(), row.getAsn(),
                        row.getSamples(), row.getAliveCount(), row.getFailovers()))
                .toList();
    }

    @Override
    public List<DomainAsnAggregate> aggregateGlobalByDomainAndAsn(Instant from, Instant to) {
        return mapper.selectGlobalGroupedByDomainAndAsn(from, to).stream()
                .map(row -> new DomainAsnAggregate(row.getFailureDomain(), row.getAsn(),
                        row.getSamples(), row.getAliveCount(), row.getFailovers()))
                .toList();
    }
}
