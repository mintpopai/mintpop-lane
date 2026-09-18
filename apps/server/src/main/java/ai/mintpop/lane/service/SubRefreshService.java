package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅定时刷新：周期性对齐各分组已有节点的参数、端口、故障域，以及分组自身的额度信息。
 * 此前订阅刷新只能管理员在管理端手动点，机场随时可能改端口/域名，一改库里就是过期配置，
 * 用户下发的配置连不上也没人知道，直到有人报障——本任务补一个定时任务持续对齐。
 * <p>
 * 一条刻意的克制：只更新**已存在**的节点（按订阅原始节点名 sourceName 匹配），订阅里新增
 * 或消失的节点都只推飞书告知、绝不自动新增或删除——启用哪些节点是运营决策，自动拉入会绕过
 * 人的判断，自动删除更危险，可能删掉正在被用户引用的节点。
 * 节点的地址或端口一旦变化，还会单独推一条飞书：这意味着此前下发给用户的配置已经失效，
 * 与「节点增删」是不同性质的事件，不能混在一条通知里。
 * <p>
 * 额度告警复用 Task 8 的 {@link TrafficAlertService}，不另写判档逻辑。
 */
@Slf4j
@Service
public class SubRefreshService {

    private final NodeGroupRepository groupRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubFetchClient subFetchClient;
    private final SubYamlParser subYamlParser;
    private final FailureDomainResolver failureDomainResolver;
    private final NodeNotifyService nodeNotifyService;
    private final TrafficAlertService trafficAlertService;

    public SubRefreshService(NodeGroupRepository groupRepository, ProxyNodeRepository nodeRepository,
                             SubFetchClient subFetchClient, SubYamlParser subYamlParser,
                             FailureDomainResolver failureDomainResolver, NodeNotifyService nodeNotifyService,
                             TrafficAlertService trafficAlertService) {
        this.groupRepository = groupRepository;
        this.nodeRepository = nodeRepository;
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.failureDomainResolver = failureDomainResolver;
        this.nodeNotifyService = nodeNotifyService;
        this.trafficAlertService = trafficAlertService;
    }

    /**
     * fixedDelay：上一轮跑完再计时，分组多、拉取慢也不会两轮叠在一起。
     * initialDelay 同样取 interval：启动后先等一轮，避免每次重启都立刻对全部分组重新拉取一遍订阅。
     */
    @Scheduled(fixedDelayString = "#{@subRefreshProperties.interval.toMillis()}",
            initialDelayString = "#{@subRefreshProperties.interval.toMillis()}")
    public void refreshAll() {
        for (NodeGroupDto group : groupRepository.findAll()) {
            try {
                refreshOne(group);
            } catch (Exception e) {
                log.warn("订阅刷新处理失败，跳过 groupId={} name={}", group.getId(), group.getName(), e);
            }
        }
    }

    private void refreshOne(NodeGroupDto group) {
        // 订阅拉取（HTTP）与故障域解析（DNS）都是外呼，必须在任何数据库写入之前完成，
        // 不能包进事务——与 AdminNodeGroupServiceImpl 对同类操作的处理一致
        SubFetchResult result = subFetchClient.fetch(group.getSubUrl());
        List<SubNode> subNodes = subYamlParser.parse(result.body());
        Map<String, String> failureDomains = resolveFailureDomains(subNodes);

        applyTrafficInfo(group, result);
        groupRepository.update(group);

        diffAndUpdateNodes(group, subNodes, failureDomains);

        trafficAlertService.checkAndNotify(group, result);
    }

    /**
     * 按订阅原始节点名（sourceName）对齐已有节点：匹配上的原地更新参数/端口/故障域；
     * 订阅里多出来的、或库里有但订阅里已经没有的，都只收集起来推一条飞书，不建也不删。
     */
    private void diffAndUpdateNodes(NodeGroupDto group, List<SubNode> subNodes, Map<String, String> failureDomains) {
        Map<String, SubNode> subByName = new LinkedHashMap<>();
        subNodes.forEach(node -> subByName.putIfAbsent(node.sourceName(), node));

        List<ProxyNodeDto> existingNodes = nodeRepository.findByGroupId(group.getId());
        Map<String, ProxyNodeDto> existingByName = new LinkedHashMap<>();
        existingNodes.forEach(node -> existingByName.putIfAbsent(node.getSourceName(), node));

        List<String> added = new ArrayList<>();
        for (SubNode sub : subNodes) {
            ProxyNodeDto existing = existingByName.get(sub.sourceName());
            if (existing == null) {
                if (!added.contains(sub.sourceName())) {
                    added.add(sub.sourceName());
                }
                continue;
            }
            updateExisting(existing, sub, failureDomains);
        }

        List<String> removed = existingNodes.stream()
                .map(ProxyNodeDto::getSourceName)
                .filter(name -> !subByName.containsKey(name))
                .toList();

        if (!added.isEmpty() || !removed.isEmpty()) {
            nodeNotifyService.notifySubNodesChanged(group, added, removed);
        }
    }

    /** 原地更新已有节点的参数/端口/故障域；端点（地址:端口）一旦变化单独推飞书——此前下发的配置已失效 */
    private void updateExisting(ProxyNodeDto node, SubNode sub, Map<String, String> failureDomains) {
        String previousEndpoint = endpointOf(node.getServerAddr(), node.getPort());
        node.setServerAddr(sub.serverAddr());
        node.setPort(sub.port());
        node.setSourceType(sub.sourceType());
        node.setSecret(sub.params());
        applyFailureDomain(node, sub.serverAddr(), failureDomains);
        nodeRepository.update(node);

        String currentEndpoint = endpointOf(node.getServerAddr(), node.getPort());
        if (!previousEndpoint.equals(currentEndpoint)) {
            nodeNotifyService.notifyNodeEndpointChanged(node, previousEndpoint, currentEndpoint);
        }
    }

    private static String endpointOf(String serverAddr, Integer port) {
        return serverAddr + ":" + port;
    }

    /**
     * 按 serverAddr 去重后逐个解析故障域，同样必须在数据库写入之前调用（DNS 外呼）。
     * 解析失败的条目不进表，调用方据此保留节点原有故障域，不抹成 null——网络抖动不代表拓扑变了。
     */
    private Map<String, String> resolveFailureDomains(List<SubNode> nodes) {
        Map<String, String> byServerAddr = new LinkedHashMap<>();
        for (String serverAddr : nodes.stream().map(SubNode::serverAddr).distinct().toList()) {
            String domain = failureDomainResolver.resolve(serverAddr);
            if (domain != null) {
                byServerAddr.put(serverAddr, domain);
            }
        }
        return byServerAddr;
    }

    /** 表里没有＝本次没解析出来，保留原值不动 */
    private void applyFailureDomain(ProxyNodeDto node, String serverAddr, Map<String, String> failureDomains) {
        String domain = failureDomains.get(serverAddr);
        if (domain != null) {
            node.setFailureDomain(domain);
            node.setFailureDomainCheckedAt(Instant.now());
        }
    }

    /** 把本次拉取带回的额度信息写进分组 DTO；三个额度字段可能都是 null（机场未返回额度头） */
    private void applyTrafficInfo(NodeGroupDto group, SubFetchResult result) {
        group.setUsedBytes(result.usedBytes());
        group.setTotalBytes(result.totalBytes());
        group.setExpiresAt(result.expiresAt());
        group.setFetchedAt(Instant.now());
    }
}
