package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EcsDnsClient;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.config.EntryIpWatchProperties;
import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.repository.EntryIpHistoryRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 中转入口 IP 巡检：服务端不带 mihomo 内核，拨不动 anytls 这类协议，测不到第一跳「通不通」，
 * 但机场的中转入口域名 TTL 只有 30 秒——就是为「被封即换 IP」准备的，服务端测得到「换没换」。
 * 入口 IP 一变，大概率意味着该入口刚被封过，是封锁事件的间接信号。
 * <p>
 * 按四个运营商视角（{@link DnsVantage}）各解析一次：中转入口常按运营商分线路返回不同 IP，
 * 只从一个视角看会漏掉另外几条线的故障。
 * <p>
 * 几个刻意的取舍，与 {@link EgressCheckService} 对探测失败的处理同一个原则：
 * - 解析失败（空列表）不写入、不清历史：网络抖动不等于入口变了，下轮再试。
 * - 比对前把 IP 列表按字典序归一：DNS 轮询会让同一组 IP 的返回顺序变来变去，
 *   不排序会把「顺序变化」误报成「换机器」。
 * - 首次观测只落基线，不告警：没有历史可比时谈不上「变更」。
 * - ASN 反查只是附注，失败（单个 IP 或整体）都不影响主流程：巡检的核心是 IP 有没有变。
 */
@Slf4j
@Service
public class EntryIpWatchService {

    private final ProxyNodeRepository nodeRepository;
    private final EntryIpHistoryRepository historyRepository;
    private final EcsDnsClient ecsDnsClient;
    private final IpAsnClient ipAsnClient;
    private final NodeNotifyService nodeNotifyService;
    private final EntryIpWatchProperties properties;

    public EntryIpWatchService(ProxyNodeRepository nodeRepository, EntryIpHistoryRepository historyRepository,
                               EcsDnsClient ecsDnsClient, IpAsnClient ipAsnClient,
                               NodeNotifyService nodeNotifyService, EntryIpWatchProperties properties) {
        this.nodeRepository = nodeRepository;
        this.historyRepository = historyRepository;
        this.ecsDnsClient = ecsDnsClient;
        this.ipAsnClient = ipAsnClient;
        this.nodeNotifyService = nodeNotifyService;
        this.properties = properties;
    }

    /**
     * fixedDelay：上一轮跑完再计时，故障域多、解析慢也不会两轮叠在一起。
     * initialDelay 同样取 interval：启动后先等一轮，避免每次重启都立刻对全部故障域巡检一遍。
     */
    @Scheduled(fixedDelayString = "#{@entryIpWatchProperties.interval.toMillis()}",
            initialDelayString = "#{@entryIpWatchProperties.interval.toMillis()}")
    public void watchAll() {
        for (String domain : nodeRepository.findDistinctFailureDomains()) {
            for (Map.Entry<DnsVantage, String> vantage : properties.getVantages().entrySet()) {
                try {
                    watchOne(domain, vantage.getKey(), vantage.getValue());
                } catch (Exception e) {
                    log.warn("入口 IP 巡检失败，跳过 domain={} vantage={}", domain, vantage.getKey(), e);
                }
            }
        }
    }

    private void watchOne(String domain, DnsVantage vantage, String subnet) {
        List<String> ips = ecsDnsClient.resolveA(domain, subnet);
        if (ips.isEmpty()) {
            // 解析不通不等于 IP 变了（与 EgressCheckService 对探测失败的处理一致）：不写入、不清历史
            log.warn("入口 IP 解析为空，本轮跳过 domain={} vantage={}", domain, vantage);
            return;
        }
        // 归一化：DNS 轮询会让同一组 IP 的返回顺序变来变去，不排序会把顺序变化误报成换机
        List<String> sortedIps = ips.stream().sorted().toList();
        String current = String.join(",", sortedIps);
        Optional<EntryIpHistory> latest = historyRepository.findLatest(domain, vantage);
        if (latest.isPresent() && latest.get().getEntryIps().equals(current)) {
            return;
        }
        EntryIpHistory record = new EntryIpHistory();
        record.setFailureDomain(domain);
        record.setVantage(vantage);
        record.setEntryIps(current);
        record.setAsns(resolveAsns(sortedIps));
        historyRepository.create(record);
        if (latest.isEmpty()) {
            // 首次观测只落基线，不告警——没有「变更」可言
            return;
        }
        log.warn("中转入口 IP 变更 domain={} vantage={} {} -> {}",
                domain, vantage, latest.get().getEntryIps(), current);
        try {
            nodeNotifyService.notifyEntryIpChanged(domain, vantage, latest.get().getEntryIps(), current);
        } catch (Exception e) {
            log.warn("入口 IP 变更通知提交失败（历史已落库）domain={}", domain, e);
        }
    }

    /**
     * 按与 entryIps 相同的顺序反查 ASN 并以逗号拼接；单个 IP 查不到在对应位置留空，
     * 全部查不到则整列返回 null（与列注释「反查失败为 NULL」一致）。反查异常同样按查不到处理，不影响主流程。
     */
    private String resolveAsns(List<String> sortedIps) {
        List<String> asns = sortedIps.stream().map(this::lookupAsnSafely).toList();
        if (asns.stream().allMatch(String::isEmpty)) {
            return null;
        }
        return String.join(",", asns);
    }

    private String lookupAsnSafely(String ip) {
        try {
            return ipAsnClient.lookupAsn(ip).orElse("");
        } catch (Exception e) {
            log.warn("ASN 反查失败，本条留空 ip={}", ip, e);
            return "";
        }
    }
}
