package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.client.IpTimezoneClient;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 落地出口 IP 定时巡检：逐个探测启用中、已登记出口 IP 的落地节点，实际出口与登记不一致时
 * 直接以实际值回填登记（时区同步按新 IP 重新解析），改完再推飞书「已变更」消息（与管理端手动回填走同一条通知）。
 * 出口 IP 一变，签发凭证的实探校验就会拦下该节点，靠人记得去管理端点「检测」不可靠，所以定时自动对齐。
 * <p>
 * 几个刻意的取舍：
 * - 探测不通不改库也不通知（只记日志）：网络抖动太常见，不通不代表 IP 变了。
 * - 出口 IP 与时区成对写入：按新 IP 解析不到时区就本轮跳过、下轮再试，绝不写入「新 IP + 旧时区」的错位组合。
 * - 巡检不看飞书是否配置：对齐登记值本身就有价值，通知只是附带；未配 webhook 时通知层自行静默。
 * - 任何异常（探测、改库、提交通知）只记日志，不让单个节点的失败中断整轮。
 */
@Slf4j
@Service
public class EgressCheckService {

    private final ProxyNodeRepository nodeRepository;
    private final EgressIpVerifier.EgressProbe egressProbe;
    private final IpTimezoneClient ipTimezoneClient;
    private final NodeNotifyService nodeNotifyService;

    public EgressCheckService(ProxyNodeRepository nodeRepository,
                              EgressIpVerifier.EgressProbe egressProbe,
                              IpTimezoneClient ipTimezoneClient,
                              NodeNotifyService nodeNotifyService) {
        this.nodeRepository = nodeRepository;
        this.egressProbe = egressProbe;
        this.ipTimezoneClient = ipTimezoneClient;
        this.nodeNotifyService = nodeNotifyService;
    }

    /**
     * fixedDelay：上一轮跑完再计时，节点多、探测慢也不会两轮叠在一起。
     * initialDelay 同样取 interval：启动后先等一轮，避免每次重启都立刻对全部出口探测一遍。
     */
    @Scheduled(fixedDelayString = "#{@egressCheckProperties.interval.toMillis()}",
            initialDelayString = "#{@egressCheckProperties.interval.toMillis()}")
    public void checkAll() {
        for (ProxyNodeDto node : nodeRepository.findAll(NodeRole.LAND)) {
            if (node.getStatus() != NodeStatus.ENABLED
                    || node.getEgressIp() == null || node.getEgressIp().isBlank()) {
                continue;
            }
            try {
                checkOne(node);
            } catch (Exception e) {
                log.warn("出口 IP 巡检处理失败，跳过 nodeId={} name={}", node.getId(), node.getName(), e);
            }
        }
    }

    private void checkOne(ProxyNodeDto node) {
        String actual;
        try {
            actual = egressProbe.currentEgressIp(node);
        } catch (Exception e) {
            log.warn("出口 IP 巡检探测不通，跳过 nodeId={} name={}", node.getId(), node.getName(), e);
            return;
        }
        String previousIp = node.getEgressIp();
        if (previousIp.equals(actual)) {
            return;
        }
        Optional<String> timezone = ipTimezoneClient.lookup(actual);
        if (timezone.isEmpty()) {
            log.warn("落地出口 IP 变动但解析不到新出口的时区，本轮不回填 nodeId={} name={} {} -> {}",
                    node.getId(), node.getName(), previousIp, actual);
            return;
        }
        String previousTimezone = node.getEgressTimezone();
        // node 来自 findAll 的完整 DTO（含明文 secret），整行回写不会丢字段
        node.setEgressIp(actual);
        node.setEgressTimezone(timezone.get());
        nodeRepository.update(node);
        log.warn("落地出口 IP 变动，已自动回填 nodeId={} name={} {} -> {}，时区 {} -> {}",
                node.getId(), node.getName(), previousIp, actual, previousTimezone, timezone.get());
        // try-catch 兜底任务「提交」阶段的异常（如停机中执行器已关闭）：@Async 只消化执行中的异常
        try {
            nodeNotifyService.notifyEgressIpChanged(node, previousIp, previousTimezone,
                    EgressIpChangeSource.EGRESS_CHECK);
        } catch (Exception e) {
            log.warn("出口 IP 变更通知任务提交失败（库已改完）nodeId={}", node.getId(), e);
        }
    }
}
