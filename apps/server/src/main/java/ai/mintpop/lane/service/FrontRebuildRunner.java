package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.FrontRebuildPreview;
import ai.mintpop.lane.service.FrontAllocationPlanner.Candidate;
import ai.mintpop.lane.service.FrontAllocationPlanner.Slot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全体重算的事务部分：锁全部订阅行 → 一次性读入候选与用户 → 容量预检 → 内存里从零重排 → 批量写回。
 * 与单用户分配用同一把锁（订阅行 FOR UPDATE），期间管理员的单用户分配会排队等待。
 * 容量不足整体回滚、用户保留旧列表，不做部分分配。拉取订阅这类外呼不在这里，在 FrontRebuildServiceImpl 里先做。
 */
@Slf4j
@Service
public class FrontRebuildRunner {

    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    private final SystemSettingService systemSettingService;
    private final Clock clock;

    public FrontRebuildRunner(AirportSubscriptionRepository airportSubscriptionRepository, ProxyNodeRepository nodeRepository,
                              UserRepository userRepository, UserFrontSubscriptionRepository userFrontSubscriptionRepository,
                              SystemSettingService systemSettingService, Clock clock) {
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.userFrontSubscriptionRepository = userFrontSubscriptionRepository;
        this.systemSettingService = systemSettingService;
        this.clock = clock;
    }

    public record RebuildResult(int userCount, int subscriptionCount) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RebuildResult applyAll() {
        FrontSettings settings = systemSettingService.frontSettings();
        List<Candidate> candidates = candidates(airportSubscriptionRepository.findAllForUpdate(), settings.region());
        List<Long> userIds = userRepository.findIdsWithActiveSubscription(clock.instant());

        int available = candidates.stream().mapToInt(c -> c.primaryCapacity(settings.bandwidthPerUserMbps())).sum();
        if (available < userIds.size()) {
            throw new BizException(BizCodeEnum.FRONT_CAPACITY_INSUFFICIENT,
                    "需要 " + userIds.size() + " 个主用名额，现有 " + available);
        }

        LinkedHashMap<Long, List<Slot>> planned = FrontAllocationPlanner.planAll(userIds, candidates, settings);
        long unassigned = planned.values().stream().filter(List::isEmpty).count();
        if (unassigned > 0) {
            // 预检通过后理论上不会发生；真发生说明算法与预检口径不一致，宁可中止也不写一份残缺分配
            throw new BizException(BizCodeEnum.FRONT_CAPACITY_INSUFFICIENT, unassigned + " 个用户分不到主用");
        }

        Map<Long, List<Long>> rows = new LinkedHashMap<>();
        planned.forEach((userId, slots) -> rows.put(userId, slots.stream().map(Slot::airportSubscriptionId).toList()));
        userFrontSubscriptionRepository.replaceAll(rows);
        log.info("全体重算完成 users={} candidates={}", userIds.size(), candidates.size());
        return new RebuildResult(userIds.size(), candidates.size());
    }

    /** 只读预检：按传入的设置（页面表单里的新值）算需要与现有名额，不加锁 */
    public FrontRebuildPreview preview(FrontSettings settings) {
        List<Candidate> candidates = candidates(airportSubscriptionRepository.findAll(), settings.region());
        int available = candidates.stream().mapToInt(c -> c.primaryCapacity(settings.bandwidthPerUserMbps())).sum();
        int required = userRepository.findIdsWithActiveSubscription(clock.instant()).size();
        return new FrontRebuildPreview(required, available, available >= required);
    }

    /** 候选 = 当前地区至少有一个节点的订阅 */
    private List<Candidate> candidates(List<AirportSubscriptionDto> subscriptions, NodeRegion region) {
        Set<Long> withNodes = nodeRepository.findAll(NodeRole.FRONT).stream()
                .filter(node -> region.matches(node.getSourceName()))
                .map(ProxyNodeDto::getAirportSubscriptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return subscriptions.stream()
                .filter(s -> withNodes.contains(s.getId()))
                .map(s -> new Candidate(s.getId(), s.getAirportId(), s.getBandwidthMbps()))
                .toList();
    }
}
