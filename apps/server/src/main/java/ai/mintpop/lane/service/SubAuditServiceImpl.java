package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EcsDnsClient;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.config.EntryIpWatchProperties;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.DnsVantage;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final EcsDnsClient ecsDnsClient;
    private final IpAsnClient ipAsnClient;
    private final ProxyNodeRepository nodeRepository;
    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final EntryIpWatchProperties entryIpWatchProperties;
    private final SystemSettingService systemSettingService;

    public SubAuditServiceImpl(SubFetchClient subFetchClient, SubYamlParser subYamlParser,
                                FailureDomainSyncer failureDomainSyncer, EcsDnsClient ecsDnsClient,
                                IpAsnClient ipAsnClient, ProxyNodeRepository nodeRepository,
                                AirportSubscriptionRepository airportSubscriptionRepository,
                                EntryIpWatchProperties entryIpWatchProperties,
                                SystemSettingService systemSettingService) {
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.failureDomainSyncer = failureDomainSyncer;
        this.ecsDnsClient = ecsDnsClient;
        this.ipAsnClient = ipAsnClient;
        this.nodeRepository = nodeRepository;
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.entryIpWatchProperties = entryIpWatchProperties;
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

        // ASN 反查按 IP 记忆：同一个入口 IP 常在多个视角、多个故障域重复出现，查一次就够
        Map<String, Optional<String>> asnCache = new HashMap<>();
        List<FailureDomainReport> failureDomains = nodesByDomain.entrySet().stream()
                .map(entry -> buildFailureDomainReport(entry.getKey(), entry.getValue(), asnCache, region))
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

    /**
     * 逐个视角查入口 IP 与对应 ASN，并判断是否分线路（各视角解析到不同 IP）。
     * <p>
     * **只对落在当前地区的故障域查**：LAND 做了国家级 GeoIP 限制，前置只能选落在当前地区的节点，
     * 港日故障域的入口查了也用不上，而 §9 采购标准里「入口 ASN ≠ AS16509」卡的本来就是美国节点的入口。
     * 这同时是外呼扇出的上限——换一家没用 CNAME 中转、每个节点自成一域的候选机场，不设限就是
     * N 个故障域 × 4 次 DoH（连接/读超时各 5s）+ 最多 4N 次 ASN 反查，全部串行、全在一个同步
     * HTTP 请求的线程里，最坏情况远超任何反代超时，管理员看到的会是 502 而不是报告。
     */
    private FailureDomainReport buildFailureDomainReport(String domain, List<SubNode> domainNodes,
                                                          Map<String, Optional<String>> asnCache,
                                                          NodeRegion region) {
        int usCount = (int) domainNodes.stream().filter(node -> region.matches(node.sourceName())).count();
        if (usCount == 0) {
            // 三个字段一律留 null 表示「本次未查询」。不能给空表或 false——
            // 那会被读成「查了但没结果」「查了，没分线路」，是比不给更糟的误导
            return new FailureDomainReport(domain, domainNodes.size(), 0, null, null, null);
        }

        Map<DnsVantage, List<String>> entryIps = new LinkedHashMap<>();
        Map<DnsVantage, List<String>> asns = new LinkedHashMap<>();
        for (Map.Entry<DnsVantage, String> vantageEntry : entryIpWatchProperties.getVantages().entrySet()) {
            DnsVantage vantage = vantageEntry.getKey();
            String clientSubnet = vantageEntry.getValue();
            List<String> ips = ecsDnsClient.resolveA(domain, clientSubnet);
            entryIps.put(vantage, ips);
            asns.put(vantage, ips.stream()
                    .map(ip -> asnCache.computeIfAbsent(ip, ipAsnClient::lookupAsn))
                    .flatMap(Optional::stream)
                    .toList());
        }

        return new FailureDomainReport(domain, domainNodes.size(), usCount, entryIps, asns,
                isLineSplit(entryIps));
    }

    /**
     * 各视角是否解析到了不同的入口 IP。两处归一缺一不可，否则都是假阳性：
     * <ul>
     *   <li>**先滤掉空列表**：某视角 DoH 失败时 {@code resolveA} 按设计返回空列表，
     *       留着它会让集合凭空多出一个元素——一次网络抖动就误报「分线路」。</li>
     *   <li>**再按字典序排**：同一组 IP 的返回顺序会抖（jp.tsdns.top 实测就是两个 AWS 东京 IP 轮询），
     *       {@code [A,B]} 与 {@code [B,A]} 不相等，同样误报。{@code EntryIpWatchService}
     *       早就先 sorted() 再比，这里对齐它。</li>
     * </ul>
     */
    private static boolean isLineSplit(Map<DnsVantage, List<String>> entryIps) {
        Set<List<String>> distinct = entryIps.values().stream()
                .filter(ips -> !ips.isEmpty())
                .map(ips -> ips.stream().sorted().toList())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return distinct.size() > 1;
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
