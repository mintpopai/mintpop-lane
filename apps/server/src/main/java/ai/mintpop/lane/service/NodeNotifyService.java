package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;

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
    public void notifyTrafficThreshold(AirportSubscriptionDto group, int percent) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("订阅", group.getName() + "（ID " + group.getId() + "）");
            fields.put("已用占比", percent + "%");
            fields.put("已用 / 总额", formatBytes(group.getUsedBytes())
                    + " / " + formatBytes(group.getTotalBytes()));
            fields.put("到期时间", group.getExpiresAt() == null
                    ? "未提供" : group.getExpiresAt().toString());
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE, "MintPop Lane 订阅额度告警", fields);
        } catch (Exception e) {
            log.warn("额度告警飞书通知失败 airportSubscriptionId={}", group.getId(), e);
        }
    }

    /**
     * 订阅即将到期 / 已过期（异步）。与额度告警共用 ORANGE 卡片：到期与额度跑满的后果一模一样——
     * 整组节点同时失效，只是到期是确定性事件、比额度更可预测，所以更该提前说。
     * 本方法不写库，调用方也不记去重状态：告警窗口只有 3 天、刷新周期 24h，重复也就两三条。
     */
    @Async
    public void notifySubscriptionExpiring(AirportSubscriptionDto group, Instant expiresAt, Duration remaining) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("订阅", group.getName() + "（ID " + group.getId() + "）");
            fields.put("到期时间", expiresAt.toString());
            fields.put("剩余", formatRemaining(remaining));
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE,
                    remaining.isNegative() ? "MintPop Lane 订阅已过期" : "MintPop Lane 订阅即将到期", fields);
        } catch (Exception e) {
            log.warn("订阅到期告警飞书通知失败 airportSubscriptionId={}", group.getId(), e);
        }
    }

    /** 剩余时长的人话展示；已过期（负数）直接说「已过期」，不显示负的小时数 */
    private static String formatRemaining(Duration remaining) {
        if (remaining.isNegative()) {
            return "已过期";
        }
        long hours = remaining.toHours();
        return hours >= 24 ? (hours / 24) + " 天 " + (hours % 24) + " 小时" : hours + " 小时";
    }

    /** 订阅拉取失败（经重试仍失败或无当前地区节点）：每 5 分钟一轮照推，不去重，直到人处理 */
    @Async
    public void notifySubFetchFailed(AirportSubscriptionDto group, String error, Instant failedSince) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("订阅", group.getName() + "（ID " + group.getId() + "）");
            fields.put("错误", error);
            fields.put("持续自", failedSince == null ? "本轮" : failedSince.toString());
            fields.put("影响", "该订阅节点保持上次成功拉取的结果，未被删除；请检查订阅链接是否失效");
            feishuBotClient.sendCard(FeishuCardTemplate.RED, "MintPop Lane 订阅拉取失败，需人工处理", fields);
        } catch (Exception e) {
            log.warn("订阅拉取失败飞书通知失败 airportSubscriptionId={}", group.getId(), e);
        }
    }

    /**
     * 中转入口 IP 已变更（异步）：机场的中转入口域名 TTL 只有 30 秒，是为「被封即换 IP」准备的，
     * 入口 IP 一变，大概率意味着该入口刚被封过——用 ORANGE，这是需要人知道的封锁事件信号，不是好消息。
     * 调用方在历史已落库之后再调本方法，通知失败不该让观测记录丢失。
     */
    @Async
    public void notifyEntryIpChanged(String failureDomain, DnsVantage vantage, String previousIps, String currentIps) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("故障域", failureDomain);
            fields.put("视角", vantage.name());
            fields.put("原入口 IP", previousIps);
            fields.put("新入口 IP", currentIps);
            fields.put("提示", "入口 IP 变更通常意味着该入口刚被封过");
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE, "MintPop Lane 中转入口 IP 已变更", fields);
        } catch (Exception e) {
            log.warn("入口 IP 变更飞书通知失败 domain={} vantage={}", failureDomain, vantage, e);
        }
    }

    /**
     * 故障域级成功率告警（异步）：跨该故障域下全部运营商求和的成功率跌破阈值。用 ORANGE——
     * 这是需要人去排查中转入口的信号，不代表用户已经完全连不上。
     * 调用方在去重状态已落库之后再调本方法，通知失败不该让去重状态丢失。
     * <p>
     * 按用户告警（不跨用户聚合）：只有一个用户劣化，多半是他的前置分配或本地网络问题，
     * 不是入口机问题，跨用户聚合会把这个信号稀释掉。带上 {@code userId}/{@code email}
     * 是为了让运维分清"多个用户各推一条"与"同一条重复推了多次"，也能一眼判断是入口机
     * 全体受影响还是个别用户的分配问题。{@code email} 由调用方（{@link LinkReportAlertService}）
     * 查出来传入——本方法不该自己查库，只负责把消息拼出来推出去，与既有 notify 方法的做法一致；
     * 查不到时传 null，展示成「用户 #id」，绝不能因为查不到 email 就不推告警。
     */
    @Async
    public void notifyFailureDomainDegraded(Long userId, String email, String failureDomain, double successRate,
                                             long samples) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("用户", displayUser(userId, email));
            fields.put("故障域", failureDomain);
            fields.put("成功率", formatRate(successRate));
            fields.put("样本量", String.valueOf(samples));
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE, "MintPop Lane 故障域成功率告警", fields);
        } catch (Exception e) {
            log.warn("故障域成功率告警飞书通知失败 userId={} domain={}", userId, failureDomain, e);
        }
    }

    /**
     * 运营商级成功率告警（异步）：同一故障域下某个运营商的成功率单独跌破阈值，与故障域级
     * 共用阈值与最小样本量，但各自独立去重。{@code userId}/{@code email} 语义同
     * {@link #notifyFailureDomainDegraded}。
     * <p>
     * ASN 与展示名两个都要：{@code asn}（形如 AS4134）是判定、分组、去重真正认的那个键，
     * {@code orgName} 是 {@code asn_org} 里记下的名字、只为让人一眼认出是哪家运营商，
     * 由调用方（{@link LinkReportAlertService}）查好传入——该 ASN 还没记过名字时传 null，
     * 此时「运营商」这一行退回展示 ASN，绝不能因为没名字就不推告警。
     */
    @Async
    public void notifyAsnDegraded(Long userId, String email, String failureDomain, String asn, String orgName,
                                   double successRate, long samples) {
        if (!notifyProperties.isConfigured()) {
            return;
        }
        try {
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("用户", displayUser(userId, email));
            fields.put("故障域", failureDomain);
            fields.put("运营商", displayOrg(asn, orgName));
            fields.put("ASN", orUnknown(asn));
            fields.put("成功率", formatRate(successRate));
            fields.put("样本量", String.valueOf(samples));
            feishuBotClient.sendCard(FeishuCardTemplate.ORANGE, "MintPop Lane 运营商成功率告警", fields);
        } catch (Exception e) {
            log.warn("运营商成功率告警飞书通知失败 userId={} domain={} asn={}", userId, failureDomain, asn, e);
        }
    }

    /**
     * 运营商展示文案：有展示名就用它（人能认出来），没有就退回 ASN（至少能查）；
     * 两个都没有才「未知」——运营商级判定只在 ASN 非空时发起，这一档纯属兜底，
     * 守的是「卡片上绝不出现字面量 null」。
     */
    private static String displayOrg(String asn, String orgName) {
        return orgName == null || orgName.isBlank() ? orUnknown(asn) : orgName;
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }

    /** 用户展示文案：有邮箱就显示邮箱，查不到（理论上不该发生）就退化成「用户 #id」，不影响告警本身推出去 */
    private static String displayUser(Long userId, String email) {
        return email == null || email.isBlank() ? "用户 #" + userId : email;
    }

    /** 成功率转百分比展示，保留一位小数；钉 Locale.ROOT 避免小数点在部分地区被渲染成逗号 */
    private static String formatRate(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    /** 字节数转 GB 展示，保留两位小数；null（机场未返回额度头）显示「未知」 */
    private static String formatBytes(Long bytes) {
        if (bytes == null) {
            return "未知";
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }
}
