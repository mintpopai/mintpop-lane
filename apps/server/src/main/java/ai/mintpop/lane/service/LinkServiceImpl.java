package ai.mintpop.lane.service;

import ai.mintpop.lane.config.FrontTuningProperties;
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
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final FrontTuningProperties frontTuningProperties;
    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final DeviceRebindRequestRepository rebindRequestRepository;
    private final UserFrontNodeRepository userFrontNodeRepository;
    private final Clock clock;

    public LinkServiceImpl(LinkProperties linkProperties,
                           FrontTuningProperties frontTuningProperties,
                           UserRepository userRepository,
                           ProxyNodeRepository nodeRepository,
                           SubscriptionRepository subscriptionRepository,
                           UserDeviceRepository userDeviceRepository,
                           DeviceRebindRequestRepository rebindRequestRepository,
                           UserFrontNodeRepository userFrontNodeRepository,
                           Clock clock) {
        this.linkProperties = linkProperties;
        this.frontTuningProperties = frontTuningProperties;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.rebindRequestRepository = rebindRequestRepository;
        this.userFrontNodeRepository = userFrontNodeRepository;
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
        ProxyNodeDto land = nodeRepository.findById(user.getLandNodeId())
                .orElseThrow(() -> new BizException(BizCodeEnum.INTERNAL_ERROR));

        // 落地节点只此一跳，没有 fallback 组可言：禁用即刻拒绝，语义与二期前一致
        if (land.getStatus() != NodeStatus.ENABLED) {
            throw new BizException(BizCodeEnum.NODE_DISABLED);
        }

        if (land.getEgressIp() == null || land.getEgressIp().isBlank()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        List<LinkConfigResponse.FrontGroup> frontGroups = resolveFrontGroups(user);
        Map<String, Object> front = frontGroups.get(0).nodes().get(0);

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
                .filter(r -> Objects.equals(r.getToDeviceId(), thisDeviceRowId))
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
                front,
                frontGroups,
                // 落地节点不接客户端的保活诉求，原样透传，不传覆盖表
                land.toMihomoNode(),
                land.getEgressIp(),
                land.getEgressTimezone(),
                credentials,
                linkProperties.getTtlSeconds()
        );
    }

    /**
     * 按故障域把用户的前置节点分组：组内滤掉非 ENABLED 的节点，空组整组丢弃；
     * 全部组都空才报 EGRESS_NOT_ASSIGNED——中间任何一步都不提前抛异常，
     * 保证「组内还有别的候选」时不会因为一个节点被禁用就整体拒绝。
     * <p>
     * 关联表为空（老数据、或分配还没跑）时退回 {@code front_node_id} 单节点，
     * 包成一个只有一个节点的组，与老客户端行为逐字一致。
     */
    private List<LinkConfigResponse.FrontGroup> resolveFrontGroups(UserDto user) {
        List<Long> frontNodeIds = userFrontNodeRepository.findNodeIdsByUserId(user.getId());
        if (frontNodeIds.isEmpty()) {
            frontNodeIds = List.of(user.getFrontNodeId());
        }

        // 外键保证节点必然存在，查不到说明数据被绕过约束改坏了，按内部错误处理
        List<ProxyNodeDto> frontNodes = frontNodeIds.stream()
                .map(id -> nodeRepository.findById(id)
                        .orElseThrow(() -> new BizException(BizCodeEnum.INTERNAL_ERROR)))
                .toList();

        // TreeMap + nullsFirst：按 failureDomain 字典序稳定排序，同时容忍尚未解析成功（null）的节点
        Map<String, List<ProxyNodeDto>> byDomain =
                new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        for (ProxyNodeDto node : frontNodes) {
            byDomain.computeIfAbsent(node.getFailureDomain(), k -> new ArrayList<>()).add(node);
        }

        List<LinkConfigResponse.FrontGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<ProxyNodeDto>> entry : byDomain.entrySet()) {
            List<Map<String, Object>> enabledNodes = entry.getValue().stream()
                    .filter(node -> node.getStatus() == NodeStatus.ENABLED)
                    // 保活参数覆盖对组里每个节点都要套，不能只套第一个，否则组内其余节点
                    // fallback 切过去就退化成每请求重握手
                    .map(node -> node.toMihomoNode(frontTuning(node)))
                    .toList();
            if (!enabledNodes.isEmpty()) {
                groups.add(new LinkConfigResponse.FrontGroup(entry.getKey(), enabledNodes));
            }
        }

        if (groups.isEmpty()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }
        return groups;
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
     * 按前置节点的真实 mihomo type（sourceType，不是 protocol）查保活参数覆盖表。
     * 手工新建的前置节点没有 sourceType（订阅导入才有），此时视同表里查不到，原样透传。
     */
    private Map<String, Object> frontTuning(ProxyNodeDto front) {
        if (front.getSourceType() == null) {
            return Map.of();
        }
        return frontTuningProperties.getProtocols().getOrDefault(front.getSourceType(), Map.of());
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
