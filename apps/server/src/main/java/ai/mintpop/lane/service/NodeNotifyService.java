package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

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
}
