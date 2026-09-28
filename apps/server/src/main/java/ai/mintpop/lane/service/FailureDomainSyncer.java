package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.parser.SubNode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 故障域的解析与写入：导入建组、订阅定时刷新、采购尽调三处共用同一份口径。
 * <p>
 * 抽出来的理由很具体：这两段此前在 {@code AdminAirportSubscriptionServiceImpl} 与 {@code SubRefreshService}
 * 里各有一份逐字相同的副本，尽调里还内联重写了第四种形态。于是「按 serverAddr 去重」
 * 「跳过伪条目」这类口径改一次得改四处，漏一处就是一次多余的 DNS 外呼扇出。
 */
@Service
public class FailureDomainSyncer {

    private final FailureDomainResolver resolver;
    private final Clock clock;

    public FailureDomainSyncer(FailureDomainResolver resolver, Clock clock) {
        this.resolver = resolver;
        this.clock = clock;
    }

    /**
     * 按 serverAddr 去重后逐个解析故障域。
     * <p>
     * **必须在事务外调用**——这是 DNS 外呼，放进事务会在查询期间独占数据库连接
     * （与本仓对订阅拉取的处理同理）。
     * <p>
     * 两条刻意的收窄：
     * <ul>
     *   <li>按 {@code serverAddr} 去重：同一台中转机在订阅里以几十个端口出现，域名却是同一个，
     *       逐个节点查纯属把同一次解析重复几十遍。</li>
     *   <li>跳过伪条目（{@code suspectedInfo}，机场塞在 proxies 里的「剩余流量」「到期时间」这种）：
     *       它们不是节点，其 server 字段也不是真实中转入口，查了只会往报告里灌噪声。</li>
     * </ul>
     * 解析失败的条目不进表，调用方据此保留节点原值——网络抖动不代表拓扑变了。
     *
     * @return serverAddr → 故障域；解析不出来的 serverAddr 不出现在表里
     */
    public Map<String, String> resolve(List<SubNode> nodes) {
        Map<String, String> byServerAddr = new LinkedHashMap<>();
        List<String> serverAddrs = nodes.stream()
                .filter(node -> !node.suspectedInfo())
                .map(SubNode::serverAddr)
                .distinct()
                .toList();
        for (String serverAddr : serverAddrs) {
            String domain = resolver.resolve(serverAddr);
            if (domain != null) {
                byServerAddr.put(serverAddr, domain);
            }
        }
        return byServerAddr;
    }

    /**
     * 把解析结果写进节点。表里没有＝本次没解析出来，保留原值不动——
     * 把旧值抹成 NULL 会让该节点退出二期的分配候选集。
     */
    public void apply(ProxyNodeDto node, String serverAddr, Map<String, String> failureDomains) {
        String domain = failureDomains.get(serverAddr);
        if (domain != null) {
            node.setFailureDomain(domain);
            node.setFailureDomainCheckedAt(clock.instant());
        }
    }
}
