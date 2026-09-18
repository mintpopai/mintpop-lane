package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 节点事件飞书通知（推给运营者的提醒，文案固定中文、不走 i18n）。
 * 尽力而为：任何异常只记日志，绝不影响改库主流程；未配置 webhook 时整体静默关闭。
 * 调用方在库已改完之后再调本方法——消息说的是「已变更」，不是「将要变」。
 */
@Slf4j
@Service
public class NodeNotifyService {

    private final NotifyProperties notifyProperties;
    private final FeishuBotClient feishuBotClient;
    private final UserRepository userRepository;

    public NodeNotifyService(NotifyProperties notifyProperties, FeishuBotClient feishuBotClient,
                             UserRepository userRepository) {
        this.notifyProperties = notifyProperties;
        this.feishuBotClient = feishuBotClient;
        this.userRepository = userRepository;
    }

    /**
     * 落地出口 IP 已变更（异步）。node 是改完后的节点，previous* 是改前登记值（未登记为 null）。
     * 时区与 IP 同步变更，所以一并展示前后值。本方法自身消化全部异常，不依赖全局异常处理器。
     */
    @Async
    public void notifyEgressIpChanged(ProxyNodeDto node, String previousEgressIp, String previousEgressTimezone,
                                      EgressIpChangeSource source) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            feishuBotClient.sendCard(source.template(), "MintPop Lane 落地出口 IP 已变更",
                    buildFields(node, previousEgressIp, previousEgressTimezone, source));
        } catch (Exception e) {
            log.warn("出口 IP 变更飞书通知失败（不影响已完成的修改）nodeId={}", node.getId(), e);
        }
    }

    /** 组装卡片字段（有序）：节点、原/新出口 IP、原/新时区、来源、绑定用户数 */
    private LinkedHashMap<String, String> buildFields(ProxyNodeDto node, String previousEgressIp,
                                                      String previousEgressTimezone, EgressIpChangeSource source) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("节点", node.getName() + "（ID " + node.getId() + "）");
        fields.put("原出口 IP", orUnregistered(previousEgressIp));
        fields.put("新出口 IP", orUnregistered(node.getEgressIp()));
        fields.put("原时区", orUnregistered(previousEgressTimezone));
        fields.put("新时区", orUnregistered(node.getEgressTimezone()));
        fields.put("来源", source.label());
        fields.put("绑定用户数", String.valueOf(userRepository.countByLandNodeId(node.getId())));
        return fields;
    }

    private static String orUnregistered(String value) {
        return value == null || value.isBlank() ? "未登记" : value;
    }

    /**
     * 订阅额度跨档（异步）。用 ORANGE：这是要人去续费或换机场的告警，不是好消息。
     * 调用方在档位已落库之后再调本方法——通知失败不该让档位丢失。
     */
    @Async
    public void notifyTrafficThreshold(NodeGroupDto group, int percent) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("分组", group.getName() + "（ID " + group.getId() + "）");
            fields.put("已用占比", percent + "%");
            fields.put("已用 / 总额", formatBytes(group.getUsedBytes())
                    + " / " + formatBytes(group.getTotalBytes()));
            fields.put("到期时间", group.getExpiresAt() == null
                    ? "未提供" : group.getExpiresAt().toString());
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE, "MintPop Lane 订阅额度告警", fields);
        } catch (Exception e) {
            log.warn("额度告警飞书通知失败 groupId={}", group.getId(), e);
        }
    }

    /**
     * 订阅节点增减（异步）：订阅定时刷新发现节点集合与库里不一致时推送，两个列表都空则不推。
     * 只告知，不代替人做决定——是否要把新节点拉进来、是否要清掉消失的节点，都需要人工确认。
     */
    @Async
    public void notifySubNodesChanged(NodeGroupDto group, List<String> added, List<String> removed) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("分组", group.getName() + "（ID " + group.getId() + "）");
            fields.put("新增节点", added.isEmpty() ? "无" : String.join("、", added));
            fields.put("消失节点", removed.isEmpty() ? "无" : String.join("、", removed));
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE, "MintPop Lane 订阅节点增减，需人工确认", fields);
        } catch (Exception e) {
            log.warn("订阅节点增减飞书通知失败 groupId={}", group.getId(), e);
        }
    }

    /**
     * 节点端点（地址:端口）已变更（异步）：意味着此前下发给用户的配置已经失效，是需要人知道的事件，
     * 与「节点增删」性质不同、单独推一条。用 RED——这是最紧急的一类，用户可能已经连不上了。
     */
    @Async
    public void notifyNodeEndpointChanged(ProxyNodeDto node, String previousEndpoint, String currentEndpoint) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("节点", node.getName() + "（ID " + node.getId() + "）");
            fields.put("原端点", previousEndpoint);
            fields.put("新端点", currentEndpoint);
            feishuBotClient.sendCard(FeishuCardTemplate.RED, "MintPop Lane 节点端点已变更，此前下发配置已失效", fields);
        } catch (Exception e) {
            log.warn("节点端点变更飞书通知失败 nodeId={}", node.getId(), e);
        }
    }

    /** 字节数转 GB 展示，保留两位小数；null（机场未返回额度头）显示「未知」 */
    private static String formatBytes(Long bytes) {
        if (bytes == null) {
            return "未知";
        }
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}
