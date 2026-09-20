package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.mapper.EntryIpHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 中转入口 IP 观测历史的 MySQL 实现。 */
@Repository
public class MybatisEntryIpHistoryRepository implements EntryIpHistoryRepository {

    private final EntryIpHistoryMapper mapper;

    public MybatisEntryIpHistoryRepository(EntryIpHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void create(EntryIpHistory history) {
        history.setId(null);
        mapper.insert(history);
    }

    @Override
    public Optional<EntryIpHistory> findLatest(String failureDomain, DnsVantage vantage) {
        // observed_at 是秒级精度，同一秒内连续写入的两行可能同值，id 自增与写入顺序一致，作二级排序键去重歧义
        return mapper.selectList(Wrappers.<EntryIpHistory>lambdaQuery()
                        .eq(EntryIpHistory::getFailureDomain, failureDomain)
                        .eq(EntryIpHistory::getVantage, vantage)
                        .orderByDesc(EntryIpHistory::getObservedAt)
                        .orderByDesc(EntryIpHistory::getId)
                        .last("LIMIT 1"))
                .stream().findFirst();
    }

    @Override
    public List<EntryIpHistory> findAllOrderByDomainVantageAndTime() {
        return mapper.selectList(Wrappers.<EntryIpHistory>lambdaQuery()
                .orderByAsc(EntryIpHistory::getFailureDomain)
                .orderByAsc(EntryIpHistory::getVantage)
                .orderByAsc(EntryIpHistory::getObservedAt)
                .orderByAsc(EntryIpHistory::getId));
    }
}
