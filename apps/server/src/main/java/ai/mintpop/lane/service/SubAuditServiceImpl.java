package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.response.SubAuditResponse;
import ai.mintpop.lane.response.SubAuditResponse.FailureDomainReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订阅尽调：给候选机场的试用订阅链接，出一份采购决策报告，全程只读、不写库。
 * <p>
 * 现实问题：两家不同品牌的机场，节点域名可能 CNAME 到同一家中转服务商——真这样的话，
 * 花两份钱买的其实是同一个故障域，入口一挂两家一起挂，冗余是假的，而且不主动查发现不了。
 * 这个接口就是主动去查：把候选订阅解析出的节点故障域，与库里已有 FRONT 节点的故障域比对，
 * 撞了就把对应订阅名列进 conflictsWith——非空即应否决这次采购。
 */
@Service
public class SubAuditServiceImpl implements SubAuditService {

    private final SubFetchClient subFetchClient;
    private final SubYamlParser subYamlParser;
    private final FailureDomainSyncer failureDomainSyncer;
    private final ProxyNodeRepository nodeRepository;
    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final SystemSettingService systemSettingService;

    public SubAuditServiceImpl(SubFetchClient subFetchClient, SubYamlParser subYamlParser,
                                FailureDomainSyncer failureDomainSyncer, ProxyNodeRepository nodeRepository,
                                AirportSubscriptionRepository airportSubscriptionRepository,
                                SystemSettingService systemSettingService) {
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.failureDomainSyncer = failureDomainSyncer;
        this.nodeRepository = nodeRepository;
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.systemSettingService = systemSettingService;
    }

    @Override
    public SubAuditResponse audit(String subUrl) {
        // 拉取/解析失败与导入完全同构，直接复用 SUB_FETCH_FAILED / SUB_PARSE_FAILED，不另造错误码
        SubFetchResult fetchResult = subFetchClient.fetch(subUrl);
        // 机场塞在 proxies 里的伪条目（「剩余流量」「到期时间」这类）不是节点，先整体剔掉：
        // 现役订阅 84 条 proxies 里有 3 条是这种，不剔的话报告会说 84 个节点、故障域的 nodeCount
        // 也跟着虚高。而这份报告是拿来卡「美国节点数 ≥ 5」的门槛，数字虚高是危险方向
        List<SubNode> nodes = subYamlParser.parse(fetchResult.body()).stream()
                .filter(node -> !node.suspectedInfo())
                .toList();

        NodeRegion region = systemSettingService.frontSettings().region();
        List<String> usNodeNames = nodes.stream()
                .map(SubNode::sourceName)
                .filter(region::matches)
                .toList();

        List<String> protocols = nodes.stream()
                .map(SubNode::sourceType)
                .distinct()
                .toList();

        // 故障域解析走与导入路径同一个 FailureDomainSyncer：**按 serverAddr 去重**。
        // 此前这里是「每个节点各查一次 JNDI DNS」，现役订阅 81 个节点只有 2 个故障域，
        // 等于把同一次解析重复了几十遍，全部串行、全在一个同步 HTTP 请求的线程里
        Map<String, String> domainByServerAddr = failureDomainSyncer.resolve(nodes);
        // 解析失败（表里没有该 serverAddr）的节点不计入任何故障域
        Map<String, List<SubNode>> nodesByDomain = new LinkedHashMap<>();
        for (SubNode node : nodes) {
            String domain = domainByServerAddr.get(node.serverAddr());
            if (domain != null) {
                nodesByDomain.computeIfAbsent(domain, key -> new ArrayList<>()).add(node);
            }
        }

        List<FailureDomainReport> failureDomains = nodesByDomain.entrySet().stream()
                .map(entry -> buildFailureDomainReport(entry.getKey(), entry.getValue(), region))
                .toList();

        List<String> conflictsWith = findConflictingGroups(nodesByDomain.keySet());

        return new SubAuditResponse(
                fetchResult.airportName(),
                nodes.size(),
                usNodeNames.size(),
                usNodeNames,
                failureDomains,
                conflictsWith,
                protocols,
                fetchResult.usedBytes(),
                fetchResult.totalBytes(),
                fetchResult.expiresAt());
    }

    /** 一个故障域的尽调结论：节点数，以及其中按名称判定为当前地区落地的节点数 */
    private static FailureDomainReport buildFailureDomainReport(String domain, List<SubNode> domainNodes,
                                                                 NodeRegion region) {
        int usCount = (int) domainNodes.stream().filter(node -> region.matches(node.sourceName())).count();
        return new FailureDomainReport(domain, domainNodes.size(), usCount);
    }

    /**
     * 候选故障域若命中库里已有 FRONT 节点的故障域，说明撞了同一家中转——
     * 按撞上的已有节点所属订阅，返回去重后的订阅名列表；未撞返回空列表。
     */
    private List<String> findConflictingGroups(Set<String> candidateDomains) {
        if (candidateDomains.isEmpty()) {
            return List.of();
        }
        Set<Long> conflictedGroupIds = nodeRepository.findAll(NodeRole.FRONT).stream()
                .filter(node -> node.getFailureDomain() != null
                        && candidateDomains.contains(node.getFailureDomain())
                        && node.getAirportSubscriptionId() != null)
                .map(ProxyNodeDto::getAirportSubscriptionId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (conflictedGroupIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> groupNames = airportSubscriptionRepository.findAll().stream()
                .collect(Collectors.toMap(AirportSubscriptionDto::getId, AirportSubscriptionDto::getName, (a, b) -> a,
                        LinkedHashMap::new));
        return conflictedGroupIds.stream()
                .map(groupNames::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
