package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.client.IpTimezoneClient;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 出口 IP 巡检：只盯「哪些节点要探、不一致时 IP 与时区成对改库并通知、失败怎么兜」。
 * 探测、GeoIP 与通知都替换成假的，不出网。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EgressCheckServiceTest {

    @Mock
    private ProxyNodeRepository nodeRepository;
    @Mock
    private IpTimezoneClient ipTimezoneClient;
    @Mock
    private NodeNotifyService nodeNotifyService;

    /** 记录探测到的节点 id，便于断言「哪些节点被探了」 */
    private final List<Long> probed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 默认任何 IP 都能解析到时区；要模拟解析失败的用例自行覆盖
        when(ipTimezoneClient.lookup(anyString())).thenReturn(Optional.of("Asia/Tokyo"));
    }

    private EgressCheckService serviceWith(EgressIpVerifier.EgressProbe probe) {
        return new EgressCheckService(nodeRepository, land -> {
            probed.add(land.getId());
            return probe.currentEgressIp(land);
        }, ipTimezoneClient, nodeNotifyService);
    }

    private ProxyNodeDto land(long id, String name, String egressIp, NodeStatus status) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setId(id);
        dto.setName(name);
        dto.setRole(NodeRole.LAND);
        dto.setEgressIp(egressIp);
        dto.setEgressTimezone("America/Los_Angeles");
        dto.setStatus(status);
        return dto;
    }

    @Test
    @DisplayName("实际出口与登记不一致：IP 与按新 IP 解析的时区一起回填，改完后以「定时巡检」来源通知，带原值")
    void backfillsIpAndTimezoneThenNotifies() {
        ProxyNodeDto node = land(7L, "LAND-1", "203.0.113.7", NodeStatus.ENABLED);
        when(nodeRepository.findAll(NodeRole.LAND)).thenReturn(List.of(node));

        serviceWith(land -> "203.0.113.9").checkAll();

        assertThat(node.getEgressIp()).isEqualTo("203.0.113.9");
        assertThat(node.getEgressTimezone()).isEqualTo("Asia/Tokyo");
        verify(ipTimezoneClient).lookup("203.0.113.9");
        verify(nodeRepository).update(node);
        verify(nodeNotifyService).notifyEgressIpChanged(node, "203.0.113.7", "America/Los_Angeles",
                EgressIpChangeSource.EGRESS_CHECK);
    }

    @Test
    @DisplayName("解析不到新出口的时区：本轮不改库不通知（IP 与时区必须成对写入），下轮再试")
    void skipsBackfillWhenTimezoneUnresolved() {
        ProxyNodeDto node = land(7L, "LAND-1", "203.0.113.7", NodeStatus.ENABLED);
        when(nodeRepository.findAll(NodeRole.LAND)).thenReturn(List.of(node));
        when(ipTimezoneClient.lookup("203.0.113.9")).thenReturn(Optional.empty());

        serviceWith(land -> "203.0.113.9").checkAll();

        assertThat(node.getEgressIp()).isEqualTo("203.0.113.7");
        assertThat(node.getEgressTimezone()).isEqualTo("America/Los_Angeles");
        verify(nodeRepository, never()).update(any());
        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("实际出口与登记一致：不查时区、不改库、不通知")
    void silentOnMatch() {
        when(nodeRepository.findAll(NodeRole.LAND))
                .thenReturn(List.of(land(7L, "LAND-1", "203.0.113.7", NodeStatus.ENABLED)));

        serviceWith(land -> "203.0.113.7").checkAll();

        verifyNoInteractions(ipTimezoneClient);
        verify(nodeRepository, never()).update(any());
        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("只探启用中且已登记出口 IP 的落地节点：停用的、未登记的跳过")
    void skipsDisabledAndUnregistered() {
        when(nodeRepository.findAll(NodeRole.LAND)).thenReturn(List.of(
                land(1L, "ON", "203.0.113.1", NodeStatus.ENABLED),
                land(2L, "OFF", "203.0.113.2", NodeStatus.DISABLED),
                land(3L, "BLANK", "  ", NodeStatus.ENABLED),
                land(4L, "NULL", null, NodeStatus.ENABLED)));

        serviceWith(land -> "203.0.113.1").checkAll();

        assertThat(probed).containsExactly(1L);
    }

    @Test
    @DisplayName("探测不通：不改库不通知，且继续探下一个节点")
    void probeFailureIsLoggedAndContinues() {
        ProxyNodeDto broken = land(1L, "BROKEN", "203.0.113.1", NodeStatus.ENABLED);
        ProxyNodeDto drifted = land(2L, "DRIFTED", "203.0.113.2", NodeStatus.ENABLED);
        when(nodeRepository.findAll(NodeRole.LAND)).thenReturn(List.of(broken, drifted));

        serviceWith(land -> {
            if (land.getId() == 1L) {
                throw new IllegalStateException("connect timeout");
            }
            return "203.0.113.99";
        }).checkAll();

        assertThat(probed).containsExactly(1L, 2L);
        assertThat(broken.getEgressIp()).isEqualTo("203.0.113.1");
        verify(nodeRepository, times(1)).update(drifted);
        verify(nodeNotifyService, times(1)).notifyEgressIpChanged(eq(drifted), eq("203.0.113.2"), any(), any());
    }

    @Test
    @DisplayName("改库失败：不通知（库没改成就不能说「已变更」），继续下一个节点")
    void updateFailureSkipsNotifyAndContinues() {
        ProxyNodeDto first = land(1L, "A", "203.0.113.1", NodeStatus.ENABLED);
        ProxyNodeDto second = land(2L, "B", "203.0.113.2", NodeStatus.ENABLED);
        when(nodeRepository.findAll(NodeRole.LAND)).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("db down")).when(nodeRepository).update(first);

        assertThatCode(() -> serviceWith(land -> "203.0.113.99").checkAll()).doesNotThrowAnyException();

        verify(nodeNotifyService, never()).notifyEgressIpChanged(eq(first), any(), any(), any());
        verify(nodeNotifyService).notifyEgressIpChanged(second, "203.0.113.2", "America/Los_Angeles",
                EgressIpChangeSource.EGRESS_CHECK);
    }

    @Test
    @DisplayName("通知任务提交失败（执行器已关闭）：库已改完，整轮继续")
    void notifySubmitFailureDoesNotAbortRound() {
        ProxyNodeDto first = land(1L, "A", "203.0.113.1", NodeStatus.ENABLED);
        ProxyNodeDto second = land(2L, "B", "203.0.113.2", NodeStatus.ENABLED);
        when(nodeRepository.findAll(NodeRole.LAND)).thenReturn(List.of(first, second));
        doThrow(new TaskRejectedException("executor shut down"))
                .when(nodeNotifyService).notifyEgressIpChanged(any(), any(), any(), any());

        assertThatCode(() -> serviceWith(land -> "203.0.113.99").checkAll()).doesNotThrowAnyException();

        verify(nodeRepository).update(first);
        verify(nodeRepository).update(second);
    }
}
