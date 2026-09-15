package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.DeviceBinding;
import ai.mintpop.lane.enumeration.LinkStatus;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final DeviceRebindRequestRepository rebindRequestRepository;
    private final Clock clock;

    public LinkServiceImpl(LinkProperties linkProperties,
                           UserRepository userRepository,
                           ProxyNodeRepository nodeRepository,
                           SubscriptionRepository subscriptionRepository,
                           UserDeviceRepository userDeviceRepository,
                           DeviceRebindRequestRepository rebindRequestRepository,
                           Clock clock) {
        this.linkProperties = linkProperties;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.rebindRequestRepository = rebindRequestRepository;
        this.clock = clock;
    }

    @Override
    public LinkConfigResponse resolveLink(Long userId, String deviceId) {
        UserDto user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(BizCodeEnum.LINK_REVOKED));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.LINK_REVOKED);
        }

        // 链路权益只看网络配置（节点分配与状态），与套餐解耦：
        // 套餐只决定下发哪些席位凭据，没买过/全过期都不拦建链
        if (user.getFrontNodeId() == null || user.getLandNodeId() == null) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        // 外键保证节点必然存在，查不到说明数据被绕过约束改坏了，按内部错误处理
        ProxyNodeDto front = nodeRepository.findById(user.getFrontNodeId())
                .orElseThrow(() -> new BizException(BizCodeEnum.INTERNAL_ERROR));
        ProxyNodeDto land = nodeRepository.findById(user.getLandNodeId())
                .orElseThrow(() -> new BizException(BizCodeEnum.INTERNAL_ERROR));

        if (front.getStatus() != NodeStatus.ENABLED || land.getStatus() != NodeStatus.ENABLED) {
            throw new BizException(BizCodeEnum.NODE_DISABLED);
        }

        if (land.getEgressIp() == null || land.getEgressIp().isBlank()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        // 已知设备与待处理申请各取一次：一个人的设备是个位数、待办更少，
        // 一次取回好过在下面逐条席位去查库
        Map<Long, UserDevice> devicesById = userDeviceRepository.findByUserId(user.getId()).stream()
                .collect(Collectors.toMap(UserDevice::getId, d -> d));
        Long thisDeviceRowId = devicesById.values().stream()
                .filter(d -> d.getDeviceId().equals(deviceId))
                .map(UserDevice::getId)
                .findFirst()
                .orElse(null);
        Set<Long> pendingFromThisDevice = rebindRequestRepository.findPendingByUserId(user.getId())
                .stream()
                .filter(r -> r.getToDeviceId().equals(thisDeviceRowId))
                .map(DeviceRebindRequest::getSubscriptionId)
                .collect(Collectors.toSet());

        // 只下发已录入凭据的在期订阅；凭据缺失只影响对应 agent 的会话，不拦建链——
        // 全部缺失也照常下发链路，客户端在会话入口单独提示。
        // 绑定关系不参与这一层过滤，只决定凭据是否置空（见下面的 bindingOf）
        Instant now = clock.instant();
        List<LinkConfigResponse.AgentCredential> credentials = subscriptionRepository
                .findByUserId(user.getId()).stream()
                .filter(s -> s.isActiveAt(now))
                .filter(s -> s.getCredential() != null && !s.getCredential().isBlank())
                .map(s -> toCredential(s, thisDeviceRowId, devicesById, pendingFromThisDevice))
                .toList();

        return new LinkConfigResponse(
                front.toMihomoNode(),
                land.toMihomoNode(),
                land.getEgressIp(),
                land.getEgressTimezone(),
                credentials,
                linkProperties.getTtlSeconds()
        );
    }

    @Override
    public HeartbeatResponse heartbeat(Long userId) {
        // 从库里消失等价于权限被收回，按吊销处理让客户端断链。
        // 订阅在期与否不参与判定：套餐只影响席位，不拦网络链路
        return userRepository.findById(userId)
                .map(user -> {
                    if (user.getStatus() == UserStatus.SUSPENDED) {
                        return new HeartbeatResponse(LinkStatus.SUSPENDED);
                    }
                    if (user.getStatus() == UserStatus.REVOKED) {
                        return new HeartbeatResponse(LinkStatus.REVOKED);
                    }
                    return new HeartbeatResponse(LinkStatus.ACTIVE);
                })
                .orElse(new HeartbeatResponse(LinkStatus.REVOKED));
    }

    /**
     * 按绑定关系组装一条席位。**这是「一份订阅只能在一台设备上用」的强制点**——
     * 凭据只在绑到本机时才真的下发，另两种情况客户端拿到的是空串。
     */
    private LinkConfigResponse.AgentCredential toCredential(
            SubscriptionDto s, Long thisDeviceRowId,
            Map<Long, UserDevice> devicesById, Set<Long> pendingFromThisDevice) {
        DeviceBinding binding = bindingOf(s.getBoundDeviceId(), thisDeviceRowId);
        boolean here = binding == DeviceBinding.BOUND_HERE;
        String boundDeviceName = binding == DeviceBinding.BOUND_ELSEWHERE
                ? Optional.ofNullable(devicesById.get(s.getBoundDeviceId()))
                        .map(UserDevice::getName).orElse("")
                : "";
        return new LinkConfigResponse.AgentCredential(
                s.getId(), s.getAssignmentNo(), s.getName(), s.getAgentType(),
                here ? s.getCredential() : "",
                here && s.getCredentialScope() != null ? s.getCredentialScope() : "",
                here && s.getCredentialOrgUuid() != null ? s.getCredentialOrgUuid() : "",
                binding,
                boundDeviceName,
                pendingFromThisDevice.contains(s.getId()),
                s.getEndsAt());
    }

    /**
     * 订阅绑在哪。本机还没被登记过（thisDeviceRowId 为 null）时，任何已绑定的订阅
     * 对它而言都是「绑在别处」——这正确：那台机器确实不是这一台
     */
    private DeviceBinding bindingOf(Long boundDeviceId, Long thisDeviceRowId) {
        if (boundDeviceId == null) {
            return DeviceBinding.UNBOUND;
        }
        return boundDeviceId.equals(thisDeviceRowId)
                ? DeviceBinding.BOUND_HERE
                : DeviceBinding.BOUND_ELSEWHERE;
    }
}
