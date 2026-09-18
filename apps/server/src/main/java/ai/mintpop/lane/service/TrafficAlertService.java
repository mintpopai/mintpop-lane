package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.repository.NodeGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 订阅额度与到期告警：额度跨到更高档才推，用量回落（月度重置）则清档；剩余时长不足三天另推一条。
 *
 * 额度耗尽与订阅到期的后果完全一样——整组节点同时失效，是「突然全挂」里最容易提前预警的两种。
 * 额度那条每轮刷新都推会刷屏，所以把「已推到哪一档」记在 node_group.traffic_alerted_pct 上。
 * 到期那条**刻意不做去重**：刷新周期是 24h、告警窗口只有 3 天，不去重也就多推两三条，
 * 不值得为它再加一列去重状态、再来一次数据库迁移。
 */
@Slf4j
@Service
public class TrafficAlertService {

    /** 告警档位，从高到低排；取第一个被跨过的 */
    private static final int[] THRESHOLDS = {95, 80};

    /** 剩余时长少于这个窗口就推到期告警（spec §6.4「剩余不足 3 天」） */
    private static final Duration EXPIRY_WINDOW = Duration.ofDays(3);

    private final NodeGroupRepository groupRepository;
    private final NodeNotifyService nodeNotifyService;
    private final Clock clock;

    public TrafficAlertService(NodeGroupRepository groupRepository, NodeNotifyService nodeNotifyService,
                               Clock clock) {
        this.groupRepository = groupRepository;
        this.nodeNotifyService = nodeNotifyService;
        this.clock = clock;
    }

    public void checkAndNotify(NodeGroupDto group, SubFetchResult result) {
        checkUsage(group, result);
        // 两条判断互不依赖：额度没跨档不代表没快到期，到期检查必须独立走一遍
        checkExpiry(group, result);
    }

    /** 用量跨档才推，回落到最低档以下就清档 */
    private void checkUsage(NodeGroupDto group, SubFetchResult result) {
        Integer percent = result.usedPercent();
        if (percent == null) {
            // 机场没返回额度头，什么都不知道——不推也不动档位
            return;
        }
        Integer reached = thresholdOf(percent);
        Integer alerted = group.getTrafficAlertedPct();
        if (reached == null) {
            // 回落到最低档以下＝额度已重置，清档让下个周期能重新推
            if (alerted != null) {
                group.setTrafficAlertedPct(null);
                groupRepository.update(group);
            }
            return;
        }
        if (alerted != null && alerted >= reached) {
            return;
        }
        group.setTrafficAlertedPct(reached);
        groupRepository.update(group);
        // 先落库再通知：通知失败不该让档位丢失，否则下轮会重复推
        try {
            nodeNotifyService.notifyTrafficThreshold(group, percent);
        } catch (Exception e) {
            log.warn("额度告警提交失败（档位已落库）groupId={}", group.getId(), e);
        }
    }

    /**
     * 剩余时长不足 {@link #EXPIRY_WINDOW} 就推一条（已过期同样推——那是仍在持续的故障，不是历史事件）。
     * 「现在」取注入的 Clock，不直接调 Instant.now()，否则这段没法测。
     * 本分支完全不写库：不去重就不需要去重状态，也就没有任何要持久化的东西。
     */
    private void checkExpiry(NodeGroupDto group, SubFetchResult result) {
        Instant expiresAt = result.expiresAt();
        if (expiresAt == null) {
            // 机场没返回到期时间（或返回了 expire=0 这种「不限期」写法），无从判断——整段跳过
            return;
        }
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.compareTo(EXPIRY_WINDOW) >= 0) {
            return;
        }
        try {
            nodeNotifyService.notifySubscriptionExpiring(group, expiresAt, remaining);
        } catch (Exception e) {
            log.warn("到期告警提交失败 groupId={}", group.getId(), e);
        }
    }

    /** @return 该占比跨过的最高档；一档都没跨返回 null */
    private Integer thresholdOf(int percent) {
        for (int threshold : THRESHOLDS) {
            if (percent >= threshold) {
                return threshold;
            }
        }
        return null;
    }
}
