package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.NodeSaveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 只盯「管理端更新节点时出口 IP 改了要不要通知」这一条分叉；更新本身的校验与落库由 AdminNodeControllerTest（真库）覆盖。
 * 时区按提交值写入（表单已联动预填、管理员也可自己改），服务端不做解析。
 */
@ExtendWith(MockitoExtension.class)
class AdminNodeEgressIpChangeNotifyTest {

    @Mock
    private ProxyNodeRepository nodeRepository;
    @Mock
    private NodeNotifyService nodeNotifyService;
    private AdminNodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminNodeServiceImpl(nodeRepository, mock(UserRepository.class),
                mock(UserFrontNodeRepository.class),
                mock(AirportSubscriptionRepository.class), land -> "unused", nodeNotifyService);
    }

    private ProxyNodeDto storedLand(String egressIp, String timezone) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setId(7L);
        dto.setName("LAND-1");
        dto.setRole(NodeRole.LAND);
        dto.setProtocol(NodeProtocol.SOCKS5);
        dto.setServerAddr("land.example.com");
        dto.setPort(1080);
        dto.setEgressIp(egressIp);
        dto.setEgressTimezone(timezone);
        dto.setStatus(NodeStatus.ENABLED);
        when(nodeRepository.findById(7L)).thenReturn(Optional.of(dto));
        return dto;
    }

    private NodeSaveRequest landRequest(String egressIp, String timezone) {
        NodeSaveRequest request = new NodeSaveRequest();
        request.setName("LAND-1");
        request.setRole(NodeRole.LAND);
        request.setProtocol(NodeProtocol.SOCKS5);
        request.setServerAddr("land.example.com");
        request.setPort(1080);
        request.setEgressIp(egressIp);
        request.setEgressTimezone(timezone);
        request.setStatus(NodeStatus.ENABLED);
        return request;
    }

    @Test
    @DisplayName("出口 IP 改了：时区按提交值写入，先落库再以「管理端」来源通知，带原 IP 与原时区")
    void notifiesAfterUpdateWhenEgressIpChanged() {
        ProxyNodeDto stored = storedLand("203.0.113.7", "America/Los_Angeles");

        service.update(7L, landRequest("203.0.113.9", "Asia/Tokyo"));

        assertThat(stored.getEgressIp()).isEqualTo("203.0.113.9");
        assertThat(stored.getEgressTimezone()).isEqualTo("Asia/Tokyo");
        InOrder inOrder = inOrder(nodeRepository, nodeNotifyService);
        inOrder.verify(nodeRepository).update(stored);
        inOrder.verify(nodeNotifyService).notifyEgressIpChanged(stored, "203.0.113.7", "America/Los_Angeles",
                EgressIpChangeSource.ADMIN);
    }

    @Test
    @DisplayName("首次登记出口 IP（检测后回填缺失值）：原值为 null 也通知")
    void notifiesOnFirstRegistration() {
        ProxyNodeDto stored = storedLand(null, null);

        service.update(7L, landRequest("203.0.113.9", "Asia/Tokyo"));

        verify(nodeNotifyService).notifyEgressIpChanged(stored, null, null, EgressIpChangeSource.ADMIN);
    }

    @Test
    @DisplayName("出口 IP 没变（只改了时区或首尾空白）：不通知")
    void silentWhenEgressIpUnchanged() {
        storedLand("203.0.113.7", "America/Los_Angeles");

        service.update(7L, landRequest(" 203.0.113.7 ", "Asia/Singapore"));

        verify(nodeRepository).update(any());
        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("通知任务提交失败（执行器已关闭）：更新照常成功，不变成 500")
    void notifySubmitFailureDoesNotBreakUpdate() {
        storedLand("203.0.113.7", null);
        doThrow(new TaskRejectedException("executor shut down"))
                .when(nodeNotifyService).notifyEgressIpChanged(any(), any(), any(), any());

        assertThatCode(() -> service.update(7L, landRequest("203.0.113.9", null))).doesNotThrowAnyException();
    }
}
