package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;

import java.util.List;
import java.util.Optional;

/** 中转入口 IP 观测历史的读写口。表没有敏感字段，直接用实体，不另设 DTO。 */
public interface EntryIpHistoryRepository {

    void create(EntryIpHistory history);

    /** 该故障域该视角的最新一条观测；从未观测过返回 empty */
    Optional<EntryIpHistory> findLatest(String failureDomain, DnsVantage vantage);

    /**
     * 全部观测历史，先按故障域、再按视角分组，组内按观测时间升序（同秒并列时用 id 兜底排序，
     * 与 {@link #findLatest} 同一原则）。供管理端计算「入口 IP 变更时间线」：
     * 同一组里相邻两条记录之间就是一次变更，组内第一条只是基线、不算变更。
     * <p>
     * 刻意全量返回、不接受时间范围参数：调用方要正确识别「进入查询窗口后的第一次变更」，
     * 必须能看到窗口之前的最后一条基线记录用于比对，时间过滤只能在算出变更列表之后按
     * 变更发生时刻（后一条记录的观测时间）做，不能下推到这一层。本表只在入口 IP 真变化时
     * 才插入新行，增长速度慢，全量拉取可接受。
     */
    List<EntryIpHistory> findAllOrderByDomainVantageAndTime();
}
