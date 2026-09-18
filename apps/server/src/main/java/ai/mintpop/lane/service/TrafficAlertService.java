package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.repository.NodeGroupRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 订阅额度告警：跨到更高档才推，用量回落（月度重置）则清档。
 *
 * 额度耗尽会让整组节点同时失效，是「突然全挂」里最容易提前预警的一种。
 * 但每轮刷新都推会刷屏，所以把「已推到哪一档」记在 node_group.traffic_alerted_pct 上。
 */
@Slf4j
@Service
public class TrafficAlertService {

    /** 告警档位，从高到低排；取第一个被跨过的 */
    private static final int[] THRESHOLDS = {95, 80};

    private final NodeGroupRepository groupRepository;
    private final NodeNotifyService nodeNotifyService;

    public TrafficAlertService(NodeGroupRepository groupRepository, NodeNotifyService nodeNotifyService) {
        this.groupRepository = groupRepository;
        this.nodeNotifyService = nodeNotifyService;
    }

    public void checkAndNotify(NodeGroupDto group, SubFetchResult result) {
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
