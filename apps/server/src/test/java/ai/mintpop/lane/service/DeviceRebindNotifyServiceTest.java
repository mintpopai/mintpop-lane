package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceRebindNotifyServiceTest {

    @Test
    @DisplayName("未配 webhook 时整体静默：一个请求都不发，也不去查库")
    void staysSilentWhenNotConfigured() {
        NotifyProperties properties = mock(NotifyProperties.class);
        when(properties.isConfigured()).thenReturn(false);
        FeishuBotClient client = mock(FeishuBotClient.class);
        DeviceRebindRequestRepository requests = mock(DeviceRebindRequestRepository.class);

        new DeviceRebindNotifyService(properties, client, requests,
                mock(SubscriptionRepository.class), mock(UserRepository.class), mock(UserDeviceRepository.class))
                .notifyRebindRequested(5L);

        verify(client, never()).sendCard(any(), anyString(), any());
    }

    @Test
    @DisplayName("查无此申请时只记日志，不发卡片、不抛异常")
    void missingRequestDoesNotThrow() {
        Fixture f = configured();
        when(f.requests.findById(5L)).thenReturn(Optional.empty());

        f.service.notifyRebindRequested(5L);

        verify(f.client, never()).sendCard(any(), anyString(), any());
    }

    @Test
    @DisplayName("卡片是橙色待办，字段带齐用户、套餐、分配号、两台设备、理由与申请号")
    void cardCarriesEverythingAdminNeeds() {
        Fixture f = configured();
        when(f.requests.findById(5L)).thenReturn(Optional.of(request("换了新电脑")));

        f.service.notifyRebindRequested(5L);

        verify(f.client).sendCard(eq(FeishuCardTemplate.ORANGE), contains("换机申请"),
                argThat(fields -> fields.get("用户").equals("u@example.com")
                        && fields.get("套餐").equals("Claude 月付")
                        && fields.get("分配号").equals("7K3M9QX2FT")
                        && fields.get("原设备").contains("办公室 iMac")
                        && fields.get("新设备").contains("月白的 MacBook")
                        && fields.get("理由").equals("换了新电脑")
                        && fields.get("申请号").equals("DR20260915120000000001")));
    }

    @Test
    @DisplayName("理由没填时写「（未填写）」，不留一个空值让人以为字段丢了")
    void blankReasonIsSpelledOut() {
        Fixture f = configured();
        when(f.requests.findById(5L)).thenReturn(Optional.of(request("   ")));

        f.service.notifyRebindRequested(5L);

        verify(f.client).sendCard(any(), anyString(),
                argThat(fields -> fields.get("理由").equals("（未填写）")));
    }

    @Test
    @DisplayName("查库抛异常也只记日志：申请已经提上来了，通知发不出去不该变成用户的失败")
    void repositoryFailureIsSwallowed() {
        Fixture f = configured();
        when(f.requests.findById(5L)).thenThrow(new RuntimeException("库挂了"));

        f.service.notifyRebindRequested(5L);

        verify(f.client, never()).sendCard(any(), anyString(), any());
    }

    /** 一组装配好的依赖，测试按需改桩 */
    private record Fixture(DeviceRebindNotifyService service, FeishuBotClient client,
                           DeviceRebindRequestRepository requests) {
    }

    /** 已配 webhook 的服务，订阅、用户、两台设备都已就位 */
    private static Fixture configured() {
        NotifyProperties properties = mock(NotifyProperties.class);
        when(properties.isConfigured()).thenReturn(true);
        when(properties.getAdminUrl()).thenReturn(null);
        FeishuBotClient client = mock(FeishuBotClient.class);
        DeviceRebindRequestRepository requests = mock(DeviceRebindRequestRepository.class);
        SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserDeviceRepository devices = mock(UserDeviceRepository.class);

        SubscriptionDto subscription = new SubscriptionDto();
        subscription.setId(11L);
        subscription.setName("Claude 月付");
        subscription.setAssignmentNo("7K3M9QX2FT");
        when(subscriptions.findById(11L)).thenReturn(Optional.of(subscription));

        UserDto user = new UserDto();
        user.setId(1L);
        user.setEmail("u@example.com");
        when(users.findById(1L)).thenReturn(Optional.of(user));

        when(devices.findById(8L)).thenReturn(Optional.of(device(8L, "办公室 iMac")));
        when(devices.findById(7L)).thenReturn(Optional.of(device(7L, "月白的 MacBook")));

        return new Fixture(
                new DeviceRebindNotifyService(properties, client, requests, subscriptions, users, devices),
                client, requests);
    }

    private static UserDevice device(Long id, String name) {
        UserDevice d = new UserDevice();
        d.setId(id);
        d.setName(name);
        d.setOs("macos 26.6.1");
        d.setModel("Mac17,9");
        return d;
    }

    private static DeviceRebindRequest request(String reason) {
        DeviceRebindRequest r = new DeviceRebindRequest();
        r.setId(5L);
        r.setRequestNo("DR20260915120000000001");
        r.setSubscriptionId(11L);
        r.setUserId(1L);
        r.setFromDeviceId(8L);
        r.setToDeviceId(7L);
        r.setReason(reason);
        r.setStatus(RebindRequestStatus.PENDING);
        return r;
    }
}
