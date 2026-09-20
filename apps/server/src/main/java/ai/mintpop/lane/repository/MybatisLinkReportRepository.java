package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.mapper.LinkReportMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public List<DomainIspAggregate> aggregateByDomainAndIsp(Long userId, Instant from, Instant to) {
        // 表规模是「用户数 × 故障域数 × 运营商数 × 窗口数」量级，按单用户取出在 Java 侧 GROUP BY 足够快，
        // 与 MybatisUserFrontNodeRepository#countUsersByNodeId 同一种做法，避免 selectMaps 的列名坑
        List<LinkReport> rows = mapper.selectList(Wrappers.<LinkReport>lambdaQuery()
                .eq(LinkReport::getUserId, userId)
                .ge(LinkReport::getWindowStart, from)
                .lt(LinkReport::getWindowStart, to));

        // 分组 key 用 record 而不是拼字符串：拼接要挑一个「绝不出现在任一字段里」的分隔符，
        // 挑错了两个不同的 (域名, 运营商) 会撞成同一组；而 isp 可以是 null，拼进字符串会变成
        // 字面量 "null"，与一个真名叫 null 的运营商无从分辨。record 的 equals/hashCode 天然
        // 逐字段比较、正确处理 null，不需要任何分隔符
        Map<DomainIspKey, List<LinkReport>> grouped = rows.stream()
                .collect(Collectors.groupingBy(r -> new DomainIspKey(r.getFailureDomain(), r.getIsp())));

        return grouped.values().stream()
                .map(group -> {
                    LinkReport first = group.get(0);
                    long samples = group.stream().mapToLong(LinkReport::getSamples).sum();
                    long aliveCount = group.stream().mapToLong(LinkReport::getAliveCount).sum();
                    long failovers = group.stream().mapToLong(LinkReport::getFailovers).sum();
                    return new DomainIspAggregate(first.getFailureDomain(), first.getIsp(),
                            samples, aliveCount, failovers);
                })
                .sorted(Comparator.comparing(DomainIspAggregate::failureDomain, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(DomainIspAggregate::isp, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public List<UserDomainIspAggregate> aggregateAllUsersByDomainAndIsp(Instant from, Instant to) {
        return mapper.selectAllUsersGroupedByDomainAndIsp(from, to).stream()
                .map(row -> new UserDomainIspAggregate(row.getUserId(), row.getFailureDomain(), row.getIsp(),
                        row.getSamples(), row.getAliveCount(), row.getFailovers()))
                .toList();
    }

    @Override
    public List<DomainIspAggregate> aggregateGlobalByDomainAndIsp(Instant from, Instant to) {
        return mapper.selectGlobalGroupedByDomainAndIsp(from, to).stream()
                .map(row -> new DomainIspAggregate(row.getFailureDomain(), row.getIsp(),
                        row.getSamples(), row.getAliveCount(), row.getFailovers()))
                .toList();
    }

    /** Java 侧分组用的复合键：故障域 + 运营商，两者都可能为 null */
    private record DomainIspKey(String failureDomain, String isp) {
    }
}
