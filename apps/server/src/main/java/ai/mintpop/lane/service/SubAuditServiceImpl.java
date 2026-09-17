package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EcsDnsClient;
import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.config.EntryIpWatchProperties;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.NodeGroupRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 订阅尽调：给候选机场的试用订阅链接，出一份采购决策报告，全程只读、不写库。
 * <p>
 * 现实问题：两家不同品牌的机场，节点域名可能 CNAME 到同一家中转服务商——真这样的话，
 * 花两份钱买的其实是同一个故障域，入口一挂两家一起挂，冗余是假的，而且不主动查发现不了。
 * 这个接口就是主动去查：把候选订阅解析出的节点故障域，与库里已有 FRONT 节点的故障域比对，
 * 撞了就把对应分组名列进 conflictsWith——非空即应否决这次采购。
 */
@Service
public class SubAuditServiceImpl implements SubAuditService {

    /** 节点名含 US/美/United States/🇺🇸 即判为美国落地。启发式，结果必须列出来给人核对，不做纯自动决策 */
    private static final Pattern US_NODE = Pattern.compile("(?i)(\\[US]|United States|美国|🇺🇸)");

    private final SubFetchClient subFetchClient;
    private final SubYamlParser subYamlParser;
    private final FailureDomainResolver failureDomainResolver;
    private final EcsDnsClient ecsDnsClient;
    private final IpAsnClient ipAsnClient;
    private final ProxyNodeRepository nodeRepository;
    private final NodeGroupRepository groupRepository;
    private final EntryIpWatchProperties entryIpWatchProperties;

    public SubAuditServiceImpl(SubFetchClient subFetchClient, SubYamlParser subYamlParser,
                                FailureDomainResolver failureDomainResolver, EcsDnsClient ecsDnsClient,
                                IpAsnClient ipAsnClient, ProxyNodeRepository nodeRepository,
                                NodeGroupRepository groupRepository,
                                EntryIpWatchProperties entryIpWatchProperties) {
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.failureDomainResolver = failureDomainResolver;
        this.ecsDnsClient = ecsDnsClient;
        this.ipAsnClient = ipAsnClient;
        this.nodeRepository = nodeRepository;
        this.groupRepository = groupRepository;
        this.entryIpWatchProperties = entryIpWatchProperties;
    }

    @Override
    public SubAuditResponse audit(String subUrl) {
        // 拉取/解析失败与导入完全同构，直接复用 SUB_FETCH_FAILED / SUB_PARSE_FAILED，不另造错误码
        SubFetchResult fetchResult = subFetchClient.fetch(subUrl);
        List<SubNode> nodes = subYamlParser.parse(fetchResult.body());

        List<String> usNodeNames = nodes.stream()
                .map(SubNode::sourceName)
                .filter(SubAuditServiceImpl::isUsNode)
                .toList();

        List<String> protocols = nodes.stream()
                .map(SubNode::sourceType)
                .distinct()
                .toList();

        // 候选节点按 serverAddr 解析故障域并分组；解析失败（resolver 返回 null）的节点不计入任何故障域
        Map<String, List<SubNode>> nodesByDomain = new LinkedHashMap<>();
        for (SubNode node : nodes) {
            String domain = failureDomainResolver.resolve(node.serverAddr());
            if (domain != null) {
                nodesByDomain.computeIfAbsent(domain, key -> new ArrayList<>()).add(node);
            }
        }

        List<FailureDomainReport> failureDomains = nodesByDomain.entrySet().stream()
                .map(entry -> buildFailureDomainReport(entry.getKey(), entry.getValue()))
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

    private static boolean isUsNode(String sourceName) {
        return sourceName != null && US_NODE.matcher(sourceName).find();
    }

    /** 逐个视角查入口 IP 与对应 ASN，并判断是否分线路（各视角结果不完全一致） */
    private FailureDomainReport buildFailureDomainReport(String domain, List<SubNode> domainNodes) {
        int usCount = (int) domainNodes.stream().filter(node -> isUsNode(node.sourceName())).count();

        Map<DnsVantage, List<String>> entryIps = new LinkedHashMap<>();
        Map<DnsVantage, List<String>> asns = new LinkedHashMap<>();
        for (Map.Entry<DnsVantage, String> vantageEntry : entryIpWatchProperties.getVantages().entrySet()) {
            DnsVantage vantage = vantageEntry.getKey();
            String clientSubnet = vantageEntry.getValue();
            List<String> ips = ecsDnsClient.resolveA(domain, clientSubnet);
            entryIps.put(vantage, ips);
            asns.put(vantage, ips.stream()
                    .map(ipAsnClient::lookupAsn)
                    .flatMap(Optional::stream)
                    .toList());
        }

        boolean lineSplit = new LinkedHashSet<>(entryIps.values()).size() > 1;
        return new FailureDomainReport(domain, domainNodes.size(), usCount, entryIps, asns, lineSplit);
    }

    /**
     * 候选故障域若命中库里已有 FRONT 节点的故障域，说明撞了同一家中转——
     * 按撞上的已有节点所属分组，返回去重后的分组名列表；未撞返回空列表。
     */
    private List<String> findConflictingGroups(Set<String> candidateDomains) {
        if (candidateDomains.isEmpty()) {
            return List.of();
        }
        Set<Long> conflictedGroupIds = nodeRepository.findAll(NodeRole.FRONT).stream()
                .filter(node -> node.getFailureDomain() != null
                        && candidateDomains.contains(node.getFailureDomain())
                        && node.getGroupId() != null)
                .map(ProxyNodeDto::getGroupId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (conflictedGroupIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> groupNames = groupRepository.findAll().stream()
                .collect(Collectors.toMap(NodeGroupDto::getId, NodeGroupDto::getName, (a, b) -> a,
                        LinkedHashMap::new));
        return conflictedGroupIds.stream()
                .map(groupNames::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
