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
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void approve(Long requestId, Long adminUserId) {
        // decide 提交后这条申请就不再是 PENDING，之后任何 decide 都会返回 false——
        // 若紧随其后的 rebindDevice 失败（网络抖动、连接超时），申请会永久停在「已同意」
        // 而订阅还绑在旧设备上，产品里没有任何一条路能自动救回这个状态。加事务后两步
        // 要么一起提交、要么一起回滚，不会停在半成品状态；decide 本身仍是一条原子条件
        // UPDATE，事务只决定它与紧随其后那条 rebindDevice 是否作为一个整体提交
        // 这里多读一次申请是为了在裁决之前拿到它的 subscriptionId；decide 内部那次读保持原样，
        // 好让它对 reject 仍是自足的一步。换机裁决是低频管理动作，多一次主键查询不值得为它改结构
        DeviceRebindRequest request = rebindRequestRepository.findById(requestId)
                .orElseThrow(() -> new BizException(BizCodeEnum.REBIND_REQUEST_NOT_FOUND));
        // 订阅存在性必须在 decide **之前**查：rebindDevice 是无条件 UPDATE，订阅已被删时它更新 0 行
        // 却不报错，于是管理员被告知「换机成功」，实际什么都没发生，而那条申请的 PENDING 已经被
        // decide 烧掉、再也回不去。先查一次，注定做不成的同意就不该动申请状态、更不该报成功
        if (subscriptionRepository.findById(request.getSubscriptionId()).isEmpty()) {
            throw new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND);
        }
        decide(requestId, RebindRequestStatus.APPROVED, adminUserId);
        // 裁决在前、改绑在后：条件 UPDATE 已经把「谁赢了这次裁决」定死，
        // 改绑因此不会被两个同时点「同意」的管理员各执行一次
        subscriptionRepository.rebindDevice(
                request.getSubscriptionId(), request.getToDeviceId(), clock.instant());
        log.info("换机申请已同意并改绑，requestId={}，subscriptionId={}",
                requestId, request.getSubscriptionId());
    }

    @Override
    @Transactional
    public void reject(Long requestId, Long adminUserId) {
        decide(requestId, RebindRequestStatus.REJECTED, adminUserId);
        log.info("换机申请已拒绝，requestId={}", requestId);
    }

    @Override
    @Transactional
    public void unbind(Long subscriptionId) {
        // 订阅不存在时 unbindDevice 只是更新 0 行、悄悄返回成功，管理员会以为解绑生效了。
        // 管理端其它订阅操作一律报 410008，这里也得一致
        if (subscriptionRepository.findById(subscriptionId).isEmpty()) {
            throw new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND);
        }
        // unbindDevice 提交后若 supersedePending 失败，绑定已清空但旧的 PENDING 申请还留着；
        // 日后它被同意时 decide 仍会成功、rebindDevice 会把订阅悄悄改绑回申请里的目标设备，
        // 违背了「强制解绑连带作废挂起申请」的设计意图。两步放进同一事务保证要么都生效、要么都不生效
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
                // 订阅已被删时统一给空串，与 assignmentNo 同一种「缺失」表达，
                // 由前端决定怎么显示（与本仓 formatAssignmentNo 对空串整段隐藏的既有约定一致）。
                // 与 DeviceRebindNotifyService.buildFields 的中文占位文案不一致是**有意为之**，别去统一：
                // 这里喂的是管理端 UI（自己渲染缺失态），那边是人直接读的飞书卡片（必须说清缺了什么）
                subscription == null ? "" : subscription.getName(),
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
