package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDeviceServiceImplTest {

    private static final Long ADMIN_ID = 2L;
    private static final Long REQUEST_ID = 5L;
    private static final Long SUB_ID = 11L;
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private UserDeviceRepository userDeviceRepository;
    private DeviceRebindRequestRepository rebindRequestRepository;
    private UserRepository userRepository;
    private AdminDeviceServiceImpl service;

    private static SubscriptionDto subscription() {
        SubscriptionDto s = new SubscriptionDto();
        s.setId(SUB_ID);
        s.setUserId(1L);
        return s;
    }

    private static DeviceRebindRequest pending() {
        DeviceRebindRequest r = new DeviceRebindRequest();
        r.setId(REQUEST_ID);
        r.setSubscriptionId(SUB_ID);
        r.setUserId(1L);
        r.setFromDeviceId(8L);
        r.setToDeviceId(7L);
        r.setStatus(RebindRequestStatus.PENDING);
        return r;
    }

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        userDeviceRepository = mock(UserDeviceRepository.class);
        rebindRequestRepository = mock(DeviceRebindRequestRepository.class);
        userRepository = mock(UserRepository.class);
        service = new AdminDeviceServiceImpl(subscriptionRepository, userDeviceRepository,
                rebindRequestRepository, userRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("同意：先把申请置为已同意，生效了才改绑")
    void approveDecidesThenRebinds() {
        when(rebindRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending()));
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(subscription()));
        when(rebindRequestRepository.decide(REQUEST_ID, RebindRequestStatus.APPROVED, ADMIN_ID, NOW))
                .thenReturn(true);

        service.approve(REQUEST_ID, ADMIN_ID);

        verify(subscriptionRepository).rebindDevice(SUB_ID, 7L, NOW);
    }

    @Test
    @DisplayName("两个管理员同时点同意：条件更新没生效的那个不改绑，并如实报「已被处理」")
    void losingTheDecisionRaceDoesNotRebind() {
        when(rebindRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending()));
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(subscription()));
        when(rebindRequestRepository.decide(anyLong(), any(), anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.approve(REQUEST_ID, ADMIN_ID))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.REBIND_REQUEST_NOT_PENDING.getCode());
        verify(subscriptionRepository, never()).rebindDevice(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("拒绝：只改申请状态，绝不碰绑定")
    void rejectLeavesBindingAlone() {
        when(rebindRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending()));
        when(rebindRequestRepository.decide(REQUEST_ID, RebindRequestStatus.REJECTED, ADMIN_ID, NOW))
                .thenReturn(true);

        service.reject(REQUEST_ID, ADMIN_ID);

        verify(subscriptionRepository, never()).rebindDevice(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("申请不存在时如实报，不当成已处理")
    void missingRequestIsReportedAsNotFound() {
        when(rebindRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(REQUEST_ID, ADMIN_ID))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.REBIND_REQUEST_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("解绑：清空绑定，并把该订阅挂着的待处理申请一并作废")
    void unbindAlsoSupersedesPendingRequests() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(subscription()));

        service.unbind(SUB_ID);

        verify(subscriptionRepository).unbindDevice(SUB_ID);
        // 绑定都没了，那条「想从 A 改绑到 B」的申请已经没有意义，留着只会让管理员困惑
        verify(rebindRequestRepository).supersedePending(SUB_ID);
    }

    @Test
    @DisplayName("解绑一份并不存在的订阅：如实报 410008，不悄悄成功")
    void unbindingMissingSubscriptionIsReportedAsNotFound() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unbind(SUB_ID))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.SUBSCRIPTION_NOT_FOUND.getCode());
        verify(subscriptionRepository, never()).unbindDevice(anyLong());
        verify(rebindRequestRepository, never()).supersedePending(anyLong());
    }

    @Test
    @DisplayName("订阅已被删时同意换机：报 410008，且申请仍留在 PENDING——注定改不成的同意不该烧掉它")
    void approvingRequestWhoseSubscriptionIsGoneFailsBeforeDeciding() {
        when(rebindRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending()));
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(REQUEST_ID, ADMIN_ID))
                .isInstanceOf(BizException.class)
                .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.SUBSCRIPTION_NOT_FOUND.getCode());
        // 关键在「之前」：decide 一旦执行，PENDING 就再也回不去了
        verify(rebindRequestRepository, never()).decide(anyLong(), any(), anyLong(), any());
        verify(subscriptionRepository, never()).rebindDevice(anyLong(), anyLong(), any());
    }
}
