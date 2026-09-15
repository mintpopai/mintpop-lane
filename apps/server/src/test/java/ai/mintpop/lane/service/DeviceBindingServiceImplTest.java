package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.request.DeviceBindRequest;
import ai.mintpop.lane.request.DeviceRebindCreateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceBindingServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long SUB_ID = 11L;
    private static final String THIS_DEVICE = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private UserDeviceRepository userDeviceRepository;
    private DeviceRebindRequestRepository rebindRequestRepository;
    private DeviceBindingServiceImpl service;

    private static DeviceBindRequest bindBody() {
        DeviceBindRequest body = new DeviceBindRequest();
        body.setName("月白的 MacBook");
        body.setOs("macos 26.6.1");
        body.setModel("Mac17,9");
        return body;
    }

    private static SubscriptionDto activeSubscription(Long boundDeviceId) {
        SubscriptionDto s = new SubscriptionDto();
        s.setId(SUB_ID);
        s.setUserId(USER_ID);
        s.setName("Claude 月付");
        s.setAssignmentNo("7K3M9QX2FT");
        s.setStartsAt(NOW.minus(1, ChronoUnit.DAYS));
        s.setEndsAt(NOW.plus(30, ChronoUnit.DAYS));
        s.setBoundDeviceId(boundDeviceId);
        return s;
    }

    private static UserDevice device(Long id) {
        UserDevice d = new UserDevice();
        d.setId(id);
        d.setUserId(USER_ID);
        d.setDeviceId(THIS_DEVICE);
        d.setName("月白的 MacBook");
        return d;
    }

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        userDeviceRepository = mock(UserDeviceRepository.class);
        rebindRequestRepository = mock(DeviceRebindRequestRepository.class);
        service = new DeviceBindingServiceImpl(subscriptionRepository, userDeviceRepository,
                rebindRequestRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        when(userDeviceRepository.upsert(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(device(7L));
    }

    @Test
    @DisplayName("未绑定的订阅：登记设备并绑上")
    void bindsUnboundSubscription() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(activeSubscription(null)));
        when(subscriptionRepository.bindDeviceIfUnbound(SUB_ID, 7L, NOW)).thenReturn(true);

        service.bind(USER_ID, SUB_ID, THIS_DEVICE, bindBody());

        verify(subscriptionRepository).bindDeviceIfUnbound(SUB_ID, 7L, NOW);
    }

    @Test
    @DisplayName("已绑在本机：幂等成功，不重复写库")
    void bindingAgainOnSameDeviceIsIdempotent() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(activeSubscription(7L)));

        service.bind(USER_ID, SUB_ID, THIS_DEVICE, bindBody());

        verify(subscriptionRepository, never()).bindDeviceIfUnbound(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("绑在别处：明确拒绝，让客户端去走换机申请")
    void bindingRejectedWhenBoundElsewhere() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(activeSubscription(8L)));

        assertThatThrownBy(() -> service.bind(USER_ID, SUB_ID, THIS_DEVICE, bindBody()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.SUBSCRIPTION_BOUND_ELSEWHERE.getCode());
    }

    @Test
    @DisplayName("条件更新没生效即被并发抢先：同样按「已绑到其它设备」拒绝，不假装成功")
    void losingTheRaceIsReportedAsBoundElsewhere() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(activeSubscription(null)));
        when(subscriptionRepository.bindDeviceIfUnbound(SUB_ID, 7L, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.bind(USER_ID, SUB_ID, THIS_DEVICE, bindBody()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.SUBSCRIPTION_BOUND_ELSEWHERE.getCode());
    }

    @Test
    @DisplayName("别人的订阅一律当作不存在，不泄露它的存在")
    void otherUsersSubscriptionLooksMissing() {
        SubscriptionDto foreign = activeSubscription(null);
        foreign.setUserId(999L);
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.bind(USER_ID, SUB_ID, THIS_DEVICE, bindBody()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.SUBSCRIPTION_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("提申请：先把该订阅的旧 PENDING 作废，再建新的")
    void requestingRebindSupersedesOldPending() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(activeSubscription(8L)));
        DeviceRebindCreateRequest body = new DeviceRebindCreateRequest();
        body.setName("月白的 MacBook");
        body.setOs("macos 26.6.1");
        body.setModel("Mac17,9");
        body.setReason("换了新电脑");

        service.requestRebind(USER_ID, SUB_ID, THIS_DEVICE, body);

        // 顺序本身就是这条测试要守的东西：先作废旧 PENDING、再建新的。
        // 若两行调换，新建的 PENDING 会被自己紧接着的 supersedePending 顺手作废掉，
        // 订阅最终一条待处理申请都没有——两个独立的 verify 测不出这种调换，必须用 InOrder 钉死顺序
        InOrder inOrder = inOrder(rebindRequestRepository);
        inOrder.verify(rebindRequestRepository).supersedePending(SUB_ID);
        inOrder.verify(rebindRequestRepository).create(any());
    }

    @Test
    @DisplayName("没绑在别处就没什么可申请的，明确拒绝")
    void requestingRebindRejectedWhenNotBoundElsewhere() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(activeSubscription(null)));

        assertThatThrownBy(() -> service.requestRebind(USER_ID, SUB_ID, THIS_DEVICE,
                new DeviceRebindCreateRequest()))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code",
                        BizCodeEnum.SUBSCRIPTION_NOT_BOUND_ELSEWHERE.getCode());
    }
}
