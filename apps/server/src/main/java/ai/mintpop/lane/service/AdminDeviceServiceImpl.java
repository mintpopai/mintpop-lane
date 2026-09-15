package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.AdminDeviceRebindRequestResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 管理员侧的设备管理。 */
@Slf4j
@Service
public class AdminDeviceServiceImpl implements AdminDeviceService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final DeviceRebindRequestRepository rebindRequestRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AdminDeviceServiceImpl(SubscriptionRepository subscriptionRepository,
                                  UserDeviceRepository userDeviceRepository,
                                  DeviceRebindRequestRepository rebindRequestRepository,
                                  UserRepository userRepository,
                                  Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.rebindRequestRepository = rebindRequestRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public List<AdminDeviceRebindRequestResponse> listRebindRequests(RebindRequestStatus status) {
        List<DeviceRebindRequest> rows = status == null
                ? rebindRequestRepository.findAll()
                : rebindRequestRepository.findByStatus(status);
        return rows.stream().map(this::toResponse).toList();
    }

    @Override
    public void approve(Long requestId, Long adminUserId) {
        DeviceRebindRequest request = decide(requestId, RebindRequestStatus.APPROVED, adminUserId);
        // 裁决在前、改绑在后：条件 UPDATE 已经把「谁赢了这次裁决」定死，
        // 改绑因此不会被两个同时点「同意」的管理员各执行一次
        subscriptionRepository.rebindDevice(
                request.getSubscriptionId(), request.getToDeviceId(), clock.instant());
        log.info("换机申请已同意并改绑，requestId={}，subscriptionId={}",
                requestId, request.getSubscriptionId());
    }

    @Override
    public void reject(Long requestId, Long adminUserId) {
        decide(requestId, RebindRequestStatus.REJECTED, adminUserId);
        log.info("换机申请已拒绝，requestId={}", requestId);
    }

    @Override
    public void unbind(Long subscriptionId) {
        subscriptionRepository.unbindDevice(subscriptionId);
        // 绑定都没了，那条「想从 A 改绑到 B」的申请已经没有意义，留着只会让管理员困惑
        rebindRequestRepository.supersedePending(subscriptionId);
        log.info("已强制解绑订阅设备，subscriptionId={}", subscriptionId);
    }

    /** 裁决一条申请并返回它。不存在与已被处理是两件事，报出去的话也不同 */
    private DeviceRebindRequest decide(Long requestId, RebindRequestStatus to, Long adminUserId) {
        DeviceRebindRequest request = rebindRequestRepository.findById(requestId)
                .orElseThrow(() -> new BizException(BizCodeEnum.REBIND_REQUEST_NOT_FOUND));
        if (!rebindRequestRepository.decide(requestId, to, adminUserId, clock.instant())) {
            throw new BizException(BizCodeEnum.REBIND_REQUEST_NOT_PENDING);
        }
        return request;
    }

    /**
     * 拼成管理端视图。这里逐条查用户与设备，没有做批量——换机申请是低频数据，
     * 一页通常个位数，为它引入一层 Map 装配不划算；真涨到三位数再优化不迟
     */
    private AdminDeviceRebindRequestResponse toResponse(DeviceRebindRequest request) {
        SubscriptionDto subscription = subscriptionRepository.findById(request.getSubscriptionId())
                .orElse(null);
        String userEmail = userRepository.findById(request.getUserId())
                .map(UserDto::getEmail).orElse("未知用户");
        return new AdminDeviceRebindRequestResponse(
                request.getId(),
                request.getRequestNo(),
                request.getSubscriptionId(),
                subscription == null ? "订阅已不存在" : subscription.getName(),
                subscription == null ? "" : subscription.getAssignmentNo(),
                request.getUserId(),
                userEmail,
                device(request.getFromDeviceId()),
                device(request.getToDeviceId()),
                request.getReason(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getDecidedAt());
    }

    /** 设备行可能已被删（用户被删会级联删设备），此时如实给 null 而不是半截对象 */
    private AdminDeviceRebindRequestResponse.Device device(Long deviceRowId) {
        if (deviceRowId == null) {
            return null;
        }
        return userDeviceRepository.findById(deviceRowId)
                .map(d -> new AdminDeviceRebindRequestResponse.Device(
                        d.getName(), d.getOs(), d.getModel()))
                .orElse(null);
    }
}
