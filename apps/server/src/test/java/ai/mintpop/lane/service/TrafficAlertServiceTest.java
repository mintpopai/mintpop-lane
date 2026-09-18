package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.repository.NodeGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅额度告警：跨档才推，重置后清档")
class TrafficAlertServiceTest {

    @Mock private NodeGroupRepository groupRepository;
    @Mock private NodeNotifyService nodeNotifyService;

    private TrafficAlertService service;

    @BeforeEach
    void setUp() {
        service = new TrafficAlertService(groupRepository, nodeNotifyService);
    }

    private NodeGroupDto group(Integer alertedPct) {
        NodeGroupDto group = new NodeGroupDto();
        group.setId(1L);
        group.setName("TaiShan Net");
        group.setTrafficAlertedPct(alertedPct);
        return group;
    }

    /** total=100，used 即百分比，省去换算 */
    private SubFetchResult used(long percent) {
        return new SubFetchResult("proxies: []", null, percent, 100L, null);
    }

    @Test
    @DisplayName("首次跨 80% 推一次并记下档位")
    void alertsOnFirstCrossing() {
        NodeGroupDto group = group(null);

        service.checkAndNotify(group, used(85));

        verify(nodeNotifyService).notifyTrafficThreshold(group, 85);
        assertThat(group.getTrafficAlertedPct()).isEqualTo(80);
        verify(groupRepository).update(group);
    }

    @Test
    @DisplayName("已在 80% 档，再次拉取仍在该档不重复推")
    void staysQuietWithinSameThreshold() {
        service.checkAndNotify(group(80), used(88));

        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("从 80% 档跨到 95% 档再推一次")
    void alertsAgainOnHigherThreshold() {
        NodeGroupDto group = group(80);

        service.checkAndNotify(group, used(96));

        verify(nodeNotifyService).notifyTrafficThreshold(group, 96);
        assertThat(group.getTrafficAlertedPct()).isEqualTo(95);
    }

    @Test
    @DisplayName("用量重置回落后清档，下次再跨 80% 能重新推")
    void resetsThresholdAfterUsageDrops() {
        NodeGroupDto group = group(95);

        service.checkAndNotify(group, used(5));
        assertThat(group.getTrafficAlertedPct()).isNull();
        verifyNoInteractions(nodeNotifyService);

        service.checkAndNotify(group, used(82));
        verify(nodeNotifyService).notifyTrafficThreshold(group, 82);
    }

    @Test
    @DisplayName("机场没返回额度头时整段跳过，不推也不改档")
    void skipsWhenQuotaUnknown() {
        NodeGroupDto group = group(null);

        service.checkAndNotify(group, new SubFetchResult("proxies: []", null, null, null, null));

        verifyNoInteractions(nodeNotifyService);
        verifyNoInteractions(groupRepository);
    }

    @Test
    @DisplayName("用量低于最低档且此前没推过：不推也不落库")
    void staysUntouchedWhenBelowLowestThresholdWithNoPriorAlert() {
        service.checkAndNotify(group(null), used(42));

        verifyNoInteractions(nodeNotifyService);
        verifyNoInteractions(groupRepository); // 关键：连一次 update 都不该有——最常见的运行态不能白白落库
    }

    @Test
    @DisplayName("通知抛异常不影响已完成的改库")
    void notifyFailureDoesNotBreakPersistence() {
        NodeGroupDto group = group(null);
        doThrow(new TaskRejectedException("执行器已关闭"))
                .when(nodeNotifyService).notifyTrafficThreshold(any(), anyInt());

        assertThatCode(() -> service.checkAndNotify(group, used(85))).doesNotThrowAnyException();
        verify(groupRepository).update(group);
    }
}
