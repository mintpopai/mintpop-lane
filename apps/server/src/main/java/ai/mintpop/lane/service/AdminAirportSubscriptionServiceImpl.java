package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.Airport;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.AirportSubscriptionCreateRequest;
import ai.mintpop.lane.request.AirportSubscriptionUpdateRequest;
import ai.mintpop.lane.response.AirportSubscriptionResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AdminAirportSubscriptionServiceImpl implements AdminAirportSubscriptionService {

    /** proxy_node.name 列的长度上限，撞名加后缀时的截断依据 */
    private static final int NODE_NAME_MAX_CODE_POINTS = 64;

    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final AirportRepository airportRepository;
    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    private final SubFetchClient subFetchClient;
    private final SubYamlParser subYamlParser;
    private final TransactionTemplate transactionTemplate;
    private final FailureDomainSyncer failureDomainSyncer;
    private final TrafficAlertService trafficAlertService;
    private final SystemSettingService systemSettingService;

    public AdminAirportSubscriptionServiceImpl(AirportSubscriptionRepository airportSubscriptionRepository,
                                     AirportRepository airportRepository, ProxyNodeRepository nodeRepository,
                                     UserRepository userRepository,
                                     UserFrontSubscriptionRepository userFrontSubscriptionRepository,
                                     SubFetchClient subFetchClient,
                                     SubYamlParser subYamlParser, TransactionTemplate transactionTemplate,
                                     FailureDomainSyncer failureDomainSyncer,
                                     TrafficAlertService trafficAlertService,
                                     SystemSettingService systemSettingService) {
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.airportRepository = airportRepository;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.userFrontSubscriptionRepository = userFrontSubscriptionRepository;
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.transactionTemplate = transactionTemplate;
        this.failureDomainSyncer = failureDomainSyncer;
        this.trafficAlertService = trafficAlertService;
        this.systemSettingService = systemSettingService;
    }

    @Override
    public Long create(AirportSubscriptionCreateRequest request) {
        if (airportSubscriptionRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_NAME_DUPLICATED);
        }
        airportRepository.findById(request.getAirportId())
                .orElseThrow(() -> new BizException(BizCodeEnum.AIRPORT_NOT_FOUND));
        // 先拉订阅再建订阅：拉取失败时不留下空订阅；
        // 拉取解析是外呼 HTTP（最坏耗时可达约 25s），不能放进事务里独占数据库连接，
        // 故只把「建订阅 + 导入」这段真正落库的操作交给 transactionTemplate 包一个事务
        FetchResult fetched = fetchAndParse(request.getSubUrl());
        List<SubNode> usNodes = regionNodes(fetched.nodes(), systemSettingService.frontSettings().region());
        Map<String, String> failureDomains = failureDomainSyncer.resolve(usNodes);

        AirportSubscriptionDto group = new AirportSubscriptionDto();
        group.setAirportId(request.getAirportId());
        group.setName(request.getName());
        group.setAccount(request.getAccount());
        group.setBandwidthMbps(request.getBandwidthMbps());
        group.setSubUrl(request.getSubUrl());
        group.setRemark(request.getRemark());
        applyTrafficInfo(group, fetched.subFetchResult());

        Long airportSubscriptionId = transactionTemplate.execute(status -> {
            Long id = wrapUniqueViolation(() -> airportSubscriptionRepository.create(group));
            importNodes(id, usNodes, failureDomains);
            return id;
        });
        // airportSubscriptionRepository.create 不会把自增主键回写到传入的 group 上，这里补上，
        // 否则 checkAndNotify 内部的 airportSubscriptionRepository.update(group) 会因 id 为 null 而更新不到任何行
        group.setId(airportSubscriptionId);
        // 放在事务外：它自己会视情况 update 落档位，且含飞书通知提交，不应牵连建订阅的事务；
        // 新建即跨档（如刚导入就已 95%）与刷新时跨档同样重要，不能只等下一轮刷新才提醒
        trafficAlertService.checkAndNotify(group, fetched.subFetchResult());
        return airportSubscriptionId;
    }

    @Override
    public List<AirportSubscriptionResponse> list() {
        Map<Long, String> airportNames = airportRepository.findAll().stream()
                .collect(Collectors.toMap(Airport::getId, Airport::getName));
        Map<Long, Long> primaryUsedBySubscription = userFrontSubscriptionRepository.countPrimaryByAirportSubscription();
        return airportSubscriptionRepository.findAll().stream()
                .map(group -> new AirportSubscriptionResponse(
                        group.getId(),
                        group.getName(),
                        group.getAirportId(),
                        airportNames.get(group.getAirportId()),
                        group.getAccount(),
                        group.getBandwidthMbps(),
                        primaryUsedBySubscription.getOrDefault(group.getId(), 0L).intValue(),
                        FrontAllocationPlanner.primaryCapacity(group.getBandwidthMbps()),
                        maskUrl(group.getSubUrl()),
                        nodeRepository.countByAirportSubscriptionId(group.getId()),
                        group.getRemark(),
                        group.getUsedBytes(),
                        group.getTotalBytes(),
                        group.getExpiresAt(),
                        group.getFetchedAt(),
                        group.getCreatedAt(),
                        group.getUpdatedAt()))
                .toList();
    }

    @Override
    public void update(Long id, AirportSubscriptionUpdateRequest request) {
        AirportSubscriptionDto group = getGroup(id);
        // 重名检查按 id 排除自身：表是 ai_ci 排序规则，只改大小写时 existsByName 会匹配到自己
        if (airportSubscriptionRepository.existsByNameExcludingId(request.getName(), id)) {
            throw new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_NAME_DUPLICATED);
        }
        group.setName(request.getName());
        group.setAccount(request.getAccount());
        group.setRemark(request.getRemark());
        wrapUniqueViolation(() -> {
            airportSubscriptionRepository.update(group);
            return null;
        });
    }

    @Override
    public void importNodes(Long id) {
        // 取订阅、拉取解析都是只读操作，同样挪到事务外，避免外呼期间占用数据库连接；
        // 只有真正落库的「更新订阅额度信息 + 导入节点」交给 transactionTemplate 包事务
        AirportSubscriptionDto group = getGroup(id);
        FetchResult fetched = fetchAndParse(group.getSubUrl());
        List<SubNode> usNodes = regionNodes(fetched.nodes(), systemSettingService.frontSettings().region());
        Map<String, String> failureDomains = failureDomainSyncer.resolve(usNodes);
        applyTrafficInfo(group, fetched.subFetchResult());
        transactionTemplate.executeWithoutResult(status -> {
            airportSubscriptionRepository.update(group);
            importNodes(id, usNodes, failureDomains);
        });
        // 放在事务外：它自己会视情况 update 落档位，且含飞书通知提交，不应牵连节点导入的事务
        trafficAlertService.checkAndNotify(group, fetched.subFetchResult());
    }

    @Override
    // 隔离级别用 READ_COMMITTED：先对该订阅行加锁（FOR UPDATE）会等待分配事务提交，
    // 但默认的 REPEATABLE_READ 下，加锁之后的普通 SELECT（existsByAirportSubscriptionId）
    // 仍可能读到事务开始时的旧快照，看不见分配事务刚提交写入的引用；READ_COMMITTED 让
    // 每条语句都用最新的读视图，加锁等待之后才能读到分配事务提交的结果
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        airportSubscriptionRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_NOT_FOUND));
        // 订阅被用户的第一跳列表引用时不能删：先给这些用户重新分配
        if (userFrontSubscriptionRepository.existsByAirportSubscriptionId(id)) {
            throw new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_IN_USE);
        }
        List<ProxyNodeDto> nodes = nodeRepository.findByAirportSubscriptionId(id);
        // 前置节点不会被用户直接引用（用户引用的是订阅，上面已检查过）；只剩落地引用要查——
        // 订阅导入的节点本应全是 FRONT，这里是防御性检查（节点被改成 LAND 后仍挂在订阅下的情形）。
        // 先整体校验再删：不做「删到一半发现被引用」的部分删除
        for (ProxyNodeDto node : nodes) {
            if (userRepository.countByLandNodeId(node.getId()) > 0) {
                throw new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_IN_USE);
            }
        }
        nodes.forEach(node -> nodeRepository.deleteById(node.getId()));
        airportSubscriptionRepository.deleteById(id);
    }

    /**
     * 唯一约束的兜底：上面的预检查（existsByName）给的是可读错误，但两个管理员同时提交仍可能撞车，
     * 那时靠数据库的唯一索引挡住。与 {@code AdminNodeServiceImpl.wrapUniqueViolation} 同一模式。
     */
    private <T> T wrapUniqueViolation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_NAME_DUPLICATED);
        }
    }

    private AirportSubscriptionDto getGroup(Long id) {
        return airportSubscriptionRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.AIRPORT_SUBSCRIPTION_NOT_FOUND));
    }

    /** fetchAndParse 的返回值：解析出的节点列表 + 该次拉取带回的额度元信息 */
    private record FetchResult(List<SubNode> nodes, SubFetchResult subFetchResult) {
    }

    private FetchResult fetchAndParse(String subUrl) {
        SubFetchResult result = subFetchClient.fetch(subUrl);
        return new FetchResult(subYamlParser.parse(result.body()), result);
    }

    /**
     * 把本次拉取带回的额度信息写进订阅 DTO。三个额度字段可能都是 null（机场未返回该头），
     * 直接透传即可——MyBatis-Plus 默认 update 策略是 NOT_NULL，null 字段不会把旧值覆盖成 null。
     * fetchedAt 则始终记录本次拉取时间，不管额度信息是否解析出来。
     */
    private void applyTrafficInfo(AirportSubscriptionDto group, SubFetchResult subFetchResult) {
        group.setUsedBytes(subFetchResult.usedBytes());
        group.setTotalBytes(subFetchResult.totalBytes());
        group.setExpiresAt(subFetchResult.expiresAt());
        group.setFetchedAt(Instant.now());
    }

    /**
     * 从订阅里挑出要导入的节点：落在当前地区（见 {@link NodeRegion}）的真节点，按原始节点名去重。
     * 不再让管理员逐个勾选——LAND 只接受美国来源，不在当前地区的节点入池也用不上；机场可枚举、
     * 命名规则写死，判定结果就是导入结果。一个都没有时报错，不建空订阅。
     */
    private List<SubNode> regionNodes(List<SubNode> nodes, NodeRegion region) {
        Map<String, SubNode> byName = new LinkedHashMap<>();
        nodes.stream()
                .filter(node -> !node.suspectedInfo() && region.matches(node.sourceName()))
                .forEach(node -> byName.putIfAbsent(node.sourceName(), node));
        if (byName.isEmpty()) {
            throw new BizException(BizCodeEnum.SUB_NO_REGION_NODES);
        }
        return List.copyOf(byName.values());
    }

    /**
     * 把订阅节点写进订阅：同订阅内 sourceName 已存在的原地更新参数
     * （名称/状态/备注是管理员的手工痕迹，不动），不存在的新建入库。
     */
    private void importNodes(Long airportSubscriptionId, List<SubNode> nodes, Map<String, String> failureDomains) {
        for (SubNode sub : nodes) {
            String selected = sub.sourceName();
            Optional<ProxyNodeDto> existing = nodeRepository.findByAirportSubscriptionIdAndSourceName(airportSubscriptionId, selected);
            if (existing.isPresent()) {
                ProxyNodeDto node = existing.get();
                node.setServerAddr(sub.serverAddr());
                node.setPort(sub.port());
                node.setSourceType(sub.sourceType());
                node.setSecret(sub.params());
                failureDomainSyncer.apply(node, sub.serverAddr(), failureDomains);
                nodeRepository.update(node);
            } else {
                ProxyNodeDto node = new ProxyNodeDto();
                node.setName(uniqueNodeName(selected));
                // 订阅导入的节点一律是前置节点：机场几乎不提供 HTTP 代理，
                // 且落地节点需要独占的干净出口 IP，共享节点不合适
                node.setRole(NodeRole.FRONT);
                node.setProtocol(NodeProtocol.MIHOMO);
                node.setServerAddr(sub.serverAddr());
                node.setPort(sub.port());
                node.setExtraConfig(Map.of());
                node.setSecret(sub.params());
                node.setAirportSubscriptionId(airportSubscriptionId);
                node.setSourceName(selected);
                node.setSourceType(sub.sourceType());
                failureDomainSyncer.apply(node, sub.serverAddr(), failureDomains);
                nodeRepository.create(node);
            }
        }
    }

    /** 撞全局唯一名时加「 (2)」「 (3)」后缀；按码点截断，不把 emoji 劈成半个代理对 */
    private String uniqueNodeName(String sourceName) {
        String base = truncateByCodePoints(sourceName, NODE_NAME_MAX_CODE_POINTS);
        if (!nodeRepository.existsByName(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String suffix = " (" + i + ")";
            String candidate = truncateByCodePoints(base, NODE_NAME_MAX_CODE_POINTS - suffix.length()) + suffix;
            if (!nodeRepository.existsByName(candidate)) {
                return candidate;
            }
        }
    }

    private String truncateByCodePoints(String s, int maxCodePoints) {
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints));
    }

    /** 回显用的打码链接：只留 scheme 与 host，token 一律不回传 */
    private String maskUrl(String subUrl) {
        try {
            URI uri = URI.create(subUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/…";
        } catch (Exception e) {
            return "（无法解析的链接）";
        }
    }
}
