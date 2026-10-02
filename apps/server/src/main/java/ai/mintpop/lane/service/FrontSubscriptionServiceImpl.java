package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.Airport;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.FrontSubscriptionBrief;
import ai.mintpop.lane.service.FrontAllocationPlanner.Assignment;
import ai.mintpop.lane.service.FrontAllocationPlanner.Candidate;
import ai.mintpop.lane.service.FrontAllocationPlanner.Slot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FrontSubscriptionServiceImpl implements FrontSubscriptionService {

    private final UserRepository userRepository;
    private final AirportRepository airportRepository;
    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final ProxyNodeRepository nodeRepository;
    private final UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    private final SystemSettingService systemSettingService;

    public FrontSubscriptionServiceImpl(UserRepository userRepository, AirportRepository airportRepository,
                                        AirportSubscriptionRepository airportSubscriptionRepository,
                                        ProxyNodeRepository nodeRepository,
                                        UserFrontSubscriptionRepository userFrontSubscriptionRepository,
                                        SystemSettingService systemSettingService) {
        this.userRepository = userRepository;
        this.airportRepository = airportRepository;
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.nodeRepository = nodeRepository;
        this.userFrontSubscriptionRepository = userFrontSubscriptionRepository;
        this.systemSettingService = systemSettingService;
    }

    /**
     * 锁住全部订阅行后再读负载、写结果：两个管理员同时分配时，后到的要等前一个提交，
     * 否则两边读到同一份主用人数，会把同一个订阅挤超容量。隔离级别用 READ_COMMITTED——
     * 默认 REPEATABLE READ 下拿到锁后的普通读仍用事务开头的旧快照，看不见前一个刚提交的分配。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<FrontSubscriptionBrief> allocate(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));

        List<AirportSubscriptionDto> subscriptions = airportSubscriptionRepository.findAllForUpdate();
        Map<Long, Long> airportOf = subscriptions.stream()
                .collect(Collectors.toMap(AirportSubscriptionDto::getId, AirportSubscriptionDto::getAirportId));

        Set<Long> usable = usableSubscriptionIds();
        Set<Long> primaryAirports = airportRepository.findPrimaryEnabledIds();
        List<Candidate> candidates = subscriptions.stream()
                .filter(s -> usable.contains(s.getId()))
                .map(s -> new Candidate(s.getId(), s.getAirportId(), s.getBandwidthMbps(),
                        primaryAirports.contains(s.getAirportId())))
                .toList();

        List<Assignment> others = userFrontSubscriptionRepository.findAllGroupedByUser().entrySet().stream()
                .filter(e -> !e.getKey().equals(userId))
                .map(e -> new Assignment(e.getKey(), e.getValue().stream()
                        .map(subId -> new Slot(airportOf.get(subId), subId))
                        .toList()))
                .toList();

        List<Slot> planned = FrontAllocationPlanner.plan(others, candidates, systemSettingService.frontSettings());
        if (planned.isEmpty()) {
            throw new BizException(BizCodeEnum.FRONT_CAPACITY_FULL);
        }
        userFrontSubscriptionRepository.replaceForUser(userId,
                planned.stream().map(Slot::airportSubscriptionId).toList(), false);
        return briefsOf(List.of(userId)).getOrDefault(userId, List.of());
    }

    /** 与 allocate 拿同一把锁（全部订阅行 FOR UPDATE），免得与并发的自动分配/全体重算交错 */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<FrontSubscriptionBrief> assignManually(Long userId, List<Long> airportSubscriptionIdsInOrder) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        List<Long> ids = airportSubscriptionIdsInOrder;
        if (ids.isEmpty() || ids.size() > FrontSettings.MAX_AIRPORTS_PER_USER) {
            throw new BizException(BizCodeEnum.FRONT_MANUAL_INVALID,
                    "至少 1 条、最多 " + FrontSettings.MAX_AIRPORTS_PER_USER + " 条");
        }

        Map<Long, AirportSubscriptionDto> subs = airportSubscriptionRepository.findAllForUpdate().stream()
                .collect(Collectors.toMap(AirportSubscriptionDto::getId, Function.identity()));
        Set<Long> usable = usableSubscriptionIds();
        Set<Long> airportsSeen = new HashSet<>();
        for (Long id : ids) {
            AirportSubscriptionDto sub = subs.get(id);
            if (sub == null) {
                throw new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_NOT_FOUND);
            }
            if (!usable.contains(id)) {
                throw new BizException(BizCodeEnum.FRONT_MANUAL_INVALID, "订阅「" + sub.getName() + "」没有当前地区的节点");
            }
            if (!airportsSeen.add(sub.getAirportId())) {
                throw new BizException(BizCodeEnum.FRONT_MANUAL_INVALID, "同一家机场只能出现一次");
            }
        }
        AirportSubscriptionDto primary = subs.get(ids.get(0));
        if (!airportRepository.findPrimaryEnabledIds().contains(primary.getAirportId())) {
            throw new BizException(BizCodeEnum.FRONT_MANUAL_INVALID, "主用所在机场是备用机场，不能当主用");
        }

        userFrontSubscriptionRepository.replaceForUser(userId, ids, true);
        return briefsOf(List.of(userId)).getOrDefault(userId, List.of());
    }

    @Override
    @Transactional
    public void clear(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        userFrontSubscriptionRepository.deleteByUserId(userId);
    }

    @Override
    public Map<Long, List<FrontSubscriptionBrief>> briefsOf(Collection<Long> userIds) {
        Map<Long, List<Long>> idsByUser = userFrontSubscriptionRepository.findSubscriptionIdsByUserIds(userIds);
        if (idsByUser.isEmpty()) {
            return Map.of();
        }
        Map<Long, AirportSubscriptionDto> subs = airportSubscriptionRepository.findAll().stream()
                .collect(Collectors.toMap(AirportSubscriptionDto::getId, Function.identity()));
        Map<Long, String> airportNames = airportRepository.findAll().stream()
                .collect(Collectors.toMap(Airport::getId, Airport::getName));
        return idsByUser.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            List<FrontSubscriptionBrief> briefs = new ArrayList<>();
            List<Long> ids = e.getValue();
            for (int position = 0; position < ids.size(); position++) {
                AirportSubscriptionDto s = subs.get(ids.get(position));
                briefs.add(new FrontSubscriptionBrief(position, s.getId(), airportNames.get(s.getAirportId()),
                        s.getName(), s.getAccount()));
            }
            return briefs;
        }));
    }

    @Override
    public Set<Long> manualUserIds() {
        return userFrontSubscriptionRepository.findManualUserIds();
    }

    /** 至少有一个落在当前地区的节点的订阅。FRONT 节点没有状态，不看 status */
    private Set<Long> usableSubscriptionIds() {
        NodeRegion region = systemSettingService.frontSettings().region();
        return nodeRepository.findAll(NodeRole.FRONT).stream()
                .filter(node -> region.matches(node.getSourceName()))
                .map(ProxyNodeDto::getAirportSubscriptionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
