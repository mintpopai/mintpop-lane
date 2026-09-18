package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;

import java.util.Optional;

/** 中转入口 IP 观测历史的读写口。表没有敏感字段，直接用实体，不另设 DTO。 */
public interface EntryIpHistoryRepository {

    void create(EntryIpHistory history);

    /** 该故障域该视角的最新一条观测；从未观测过返回 empty */
    Optional<EntryIpHistory> findLatest(String failureDomain, DnsVantage vantage);
}
