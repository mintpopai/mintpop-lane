package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.FrontRebuildPreview;
import ai.mintpop.lane.service.FrontAllocationPlanner.Assignment;
import ai.mintpop.lane.service.FrontAllocationPlanner.Candidate;
import ai.mintpop.lane.service.FrontAllocationPlanner.Slot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
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
 * <p>
 * keepManual 时管理员手动指定的列表原样保留：这些用户不参与重排，但占的主用名额与各场景落点照算进负载，
 * 预检的「现有名额」也先扣掉他们占的；否则（覆盖）所有人从零重排，手动标记全部清掉。
 */
@Slf4j
@Service
public class FrontRebuildRunner {

    private final AirportRepository airportRepository;
    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    private final SystemSettingService systemSettingService;
    private final Clock clock;

    public FrontRebuildRunner(AirportRepository airportRepository,
                              AirportSubscriptionRepository airportSubscriptionRepository, ProxyNodeRepository nodeRepository,
                              UserRepository userRepository, UserFrontSubscriptionRepository userFrontSubscriptionRepository,
                              SystemSettingService systemSettingService, Clock clock) {
        this.airportRepository = airportRepository;
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.userFrontSubscriptionRepository = userFrontSubscriptionRepository;
        this.systemSettingService = systemSettingService;
        this.clock = clock;
    }

    /** userCount 为本次重排的用户数，keptManualCount 为原样保留的手动分配用户数（覆盖模式恒为 0） */
    public record RebuildResult(int userCount, int subscriptionCount, int keptManualCount) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RebuildResult applyAll(boolean keepManual) {
        FrontSettings settings = systemSettingService.frontSettings();
        List<AirportSubscriptionDto> subscriptions = airportSubscriptionRepository.findAllForUpdate();
        List<Candidate> candidates = candidates(subscriptions, settings.region());
        Map<Long, Long> airportOf = subscriptions.stream()
                .collect(Collectors.toMap(AirportSubscriptionDto::getId, AirportSubscriptionDto::getAirportId));
        List<Assignment> kept = keepManual ? manualAssignments(airportOf) : List.of();
        Set<Long> keptUserIds = kept.stream().map(Assignment::userId).collect(Collectors.toSet());
        List<Long> userIds = userRepository.findIdsWithActiveSubscription(clock.instant()).stream()
                .filter(id -> !keptUserIds.contains(id))
                .toList();

        int available = availablePrimary(candidates, settings, kept);
        if (available < userIds.size()) {
            throw new BizException(BizCodeEnum.FRONT_CAPACITY_INSUFFICIENT,
                    "需要 " + userIds.size() + " 个主用名额，现有 " + available);
        }

        LinkedHashMap<Long, List<Slot>> planned = FrontAllocationPlanner.planAll(userIds, candidates, settings, kept);
        long unassigned = planned.values().stream().filter(List::isEmpty).count();
        if (unassigned > 0) {
            // 预检通过后理论上不会发生；真发生说明算法与预检口径不一致，宁可中止也不写一份残缺分配
            throw new BizException(BizCodeEnum.FRONT_CAPACITY_INSUFFICIENT, unassigned + " 个用户分不到主用");
        }

        Map<Long, List<Long>> rows = new LinkedHashMap<>();
        planned.forEach((userId, slots) -> rows.put(userId, slots.stream().map(Slot::airportSubscriptionId).toList()));
        userFrontSubscriptionRepository.replaceAll(rows, keptUserIds);
        log.info("全体重算完成 users={} candidates={} keptManual={}", userIds.size(), candidates.size(), kept.size());
        return new RebuildResult(userIds.size(), candidates.size(), kept.size());
    }

    /** 只读预检：按传入的设置（页面表单里的新值）与重算方式算需要与现有名额，不加锁 */
    public FrontRebuildPreview preview(FrontSettings settings, boolean keepManual) {
        List<AirportSubscriptionDto> subscriptions = airportSubscriptionRepository.findAll();
        List<Candidate> candidates = candidates(subscriptions, settings.region());
        Map<Long, Long> airportOf = subscriptions.stream()
                .collect(Collectors.toMap(AirportSubscriptionDto::getId, AirportSubscriptionDto::getAirportId));
        List<Assignment> kept = keepManual ? manualAssignments(airportOf) : List.of();
        Set<Long> keptUserIds = kept.stream().map(Assignment::userId).collect(Collectors.toSet());
        int required = (int) userRepository.findIdsWithActiveSubscription(clock.instant()).stream()
                .filter(id -> !keptUserIds.contains(id))
                .count();
        int available = availablePrimary(candidates, settings, kept);
        return new FrontRebuildPreview(required, available, available >= required, kept.size());
    }

    /** 当前手动指定的列表，作为不参与重排的固定负载 */
    private List<Assignment> manualAssignments(Map<Long, Long> airportOf) {
        Set<Long> manualUserIds = userFrontSubscriptionRepository.findManualUserIds();
        if (manualUserIds.isEmpty()) {
            return List.of();
        }
        return userFrontSubscriptionRepository.findSubscriptionIdsByUserIds(manualUserIds).entrySet().stream()
                .map(e -> new Assignment(e.getKey(), e.getValue().stream()
                        .map(subId -> new Slot(airportOf.get(subId), subId))
                        .toList()))
                .toList();
    }

    /** 各候选订阅的主用名额减去保留的手动分配已占的人数（超额的按 0 计），再求和 */
    private static int availablePrimary(List<Candidate> candidates, FrontSettings settings, List<Assignment> kept) {
        Map<Long, Integer> keptPrimary = new HashMap<>();
        kept.forEach(a -> keptPrimary.merge(a.slots().get(0).airportSubscriptionId(), 1, Integer::sum));
        return candidates.stream()
                .mapToInt(c -> Math.max(0, c.primaryCapacity(settings.bandwidthPerUserMbps())
                        - keptPrimary.getOrDefault(c.airportSubscriptionId(), 0)))
                .sum();
    }

    /** 候选 = 当前地区至少有一个节点的订阅；带上所属机场的主用标记，非主用机场的订阅不计主用名额 */
    private List<Candidate> candidates(List<AirportSubscriptionDto> subscriptions, NodeRegion region) {
        Set<Long> primaryAirports = airportRepository.findPrimaryEnabledIds();
        Set<Long> withNodes = nodeRepository.findAll(NodeRole.FRONT).stream()
                .filter(node -> region.matches(node.getSourceName()))
                .map(ProxyNodeDto::getAirportSubscriptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return subscriptions.stream()
                .filter(s -> withNodes.contains(s.getId()))
                .map(s -> new Candidate(s.getId(), s.getAirportId(), s.getBandwidthMbps(),
                        primaryAirports.contains(s.getAirportId())))
                .toList();
    }
}
