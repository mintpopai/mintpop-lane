package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅额度与到期告警：额度跨到更高档才推，用量回落（月度重置）则清档；剩余时长不足三天另推一条。
 *
 * 额度耗尽与订阅到期的后果完全一样——整组节点同时失效，是「突然全挂」里最容易提前预警的两种。
 * 额度那条每轮刷新都推会刷屏，所以把「已推到哪一档」记在 airport_subscription.traffic_alerted_pct 上。
 * 到期那条不落库去重，改用进程内的 24 小时抑制：同一订阅 24h 内最多推一张卡片
 * （刷新周期 5 分钟，不抑制会每 5 分钟一张）；订阅离开告警窗口（续期）就清掉记录，下次进窗口再推。
 * 进程重启会丢失记录、多推一张，可以接受，不值得为它加列做数据库迁移。
 */
@Slf4j
@Service
public class TrafficAlertService {

    /** 告警档位，从高到低排；取第一个被跨过的 */
    private static final int[] THRESHOLDS = {95, 80};

    /** 剩余时长少于这个窗口就推到期告警（spec §6.4「剩余不足 3 天」） */
    private static final Duration EXPIRY_WINDOW = Duration.ofDays(3);

    /** 同一订阅到期告警的最小间隔 */
    private static final Duration EXPIRY_ALERT_INTERVAL = Duration.ofHours(24);

    /** 订阅 ID -> 上次推到期卡片的时刻（进程内，重启丢失） */
    private final ConcurrentHashMap<Long, Instant> lastExpiryAlertAt = new ConcurrentHashMap<>();

    private final AirportSubscriptionRepository airportSubscriptionRepository;
    private final NodeNotifyService nodeNotifyService;
    private final Clock clock;

    public TrafficAlertService(AirportSubscriptionRepository airportSubscriptionRepository, NodeNotifyService nodeNotifyService,
                               Clock clock) {
        this.airportSubscriptionRepository = airportSubscriptionRepository;
        this.nodeNotifyService = nodeNotifyService;
        this.clock = clock;
    }

    public void checkAndNotify(AirportSubscriptionDto group, SubFetchResult result) {
        checkUsage(group, result);
        // 两条判断互不依赖：额度没跨档不代表没快到期，到期检查必须独立走一遍
        checkExpiry(group, result);
    }

    /** 用量跨档才推，回落到最低档以下就清档 */
    private void checkUsage(AirportSubscriptionDto group, SubFetchResult result) {
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
                airportSubscriptionRepository.update(group);
            }
            return;
        }
        if (alerted != null && alerted >= reached) {
            return;
        }
        group.setTrafficAlertedPct(reached);
        airportSubscriptionRepository.update(group);
        // 先落库再通知：通知失败不该让档位丢失，否则下轮会重复推
        try {
            nodeNotifyService.notifyTrafficThreshold(group, percent);
        } catch (Exception e) {
            log.warn("额度告警提交失败（档位已落库）airportSubscriptionId={}", group.getId(), e);
        }
    }

    /**
     * 剩余时长不足 {@link #EXPIRY_WINDOW} 就推一条（已过期同样推——那是仍在持续的故障，不是历史事件）。
     * 「现在」取注入的 Clock，不直接调 Instant.now()，否则这段没法测。
     * 同一订阅 24h 内只推一次（进程内记录，见 {@link #lastExpiryAlertAt}）；离开窗口即清记录。本分支不写库。
     */
    private void checkExpiry(AirportSubscriptionDto group, SubFetchResult result) {
        Instant expiresAt = result.expiresAt();
        if (expiresAt == null) {
            // 机场没返回到期时间（或返回了 expire=0 这种「不限期」写法），无从判断——整段跳过
            return;
        }
        Instant now = clock.instant();
        Duration remaining = Duration.between(now, expiresAt);
        if (remaining.compareTo(EXPIRY_WINDOW) >= 0) {
            // 已续期、离开告警窗口：清记录，下次再进窗口能重新推
            lastExpiryAlertAt.remove(group.getId());
            return;
        }
        Instant last = lastExpiryAlertAt.get(group.getId());
        if (last != null && Duration.between(last, now).compareTo(EXPIRY_ALERT_INTERVAL) < 0) {
            return;
        }
        lastExpiryAlertAt.put(group.getId(), now);
        try {
            nodeNotifyService.notifySubscriptionExpiring(group, expiresAt, remaining);
        } catch (Exception e) {
            log.warn("到期告警提交失败 airportSubscriptionId={}", group.getId(), e);
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
