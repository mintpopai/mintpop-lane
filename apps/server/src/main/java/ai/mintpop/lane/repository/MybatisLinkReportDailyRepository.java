package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.mapper.LinkReportDailyMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/** 链路上报按天聚合的 MySQL 实现。 */
@Repository
public class MybatisLinkReportDailyRepository implements LinkReportDailyRepository {

    private final LinkReportDailyMapper mapper;

    public MybatisLinkReportDailyRepository(LinkReportDailyMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LinkReportDaily> find(Long userId, String failureDomain, String isp, LocalDate statDate) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<LinkReportDaily>lambdaQuery()
                .eq(LinkReportDaily::getUserId, userId)
                .eq(LinkReportDaily::getFailureDomain, failureDomain)
                .eq(LinkReportDaily::getIsp, isp)
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
}
