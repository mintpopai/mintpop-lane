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
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    private final SubscriptionRenderCache renderCache;
    private final Clock clock;

    public LinkServiceImpl(LinkProperties linkProperties,
                           UserRepository userRepository,
                           ProxyNodeRepository nodeRepository,
                           SubscriptionRepository subscriptionRepository,
                           UserDeviceRepository userDeviceRepository,
                           DeviceRebindRequestRepository rebindRequestRepository,
                           UserFrontSubscriptionRepository userFrontSubscriptionRepository,
                           SubscriptionRenderCache renderCache,
                           Clock clock) {
        this.linkProperties = linkProperties;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.rebindRequestRepository = rebindRequestRepository;
        this.userFrontSubscriptionRepository = userFrontSubscriptionRepository;
        this.renderCache = renderCache;
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
        RoutePart route = renderRoute(user);

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

        LinkConfigResponse response = new LinkConfigResponse(route.frontGroups(),
                // 落地节点不接客户端的保活诉求，原样透传，不传覆盖表
                route.land().toMihomoNode(), route.land().getEgressIp(), route.land().getEgressTimezone(),
                credentials, linkProperties.getTtlSeconds(), null);
        return response.withConfigVersion(LinkConfigVersion.of(routeOnly(route)));
    }

    /** 线路部分（第一跳组 + 落地节点），不含席位凭据；下发与心跳共用，保证两处算出同一个 configVersion */
    private record RoutePart(List<LinkConfigResponse.FrontGroup> frontGroups, ProxyNodeDto land) {
    }

    /** 抛 BizException 的情形：未分配落地/落地禁用/无出口 IP/未分配第一跳/全部订阅无节点 */
    private RoutePart renderRoute(UserDto user) {
        if (user.getLandNodeId() == null) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }
        // 外键保证节点必然存在，查不到说明数据被绕过约束改坏了，按内部错误处理
        ProxyNodeDto land = nodeRepository.findById(user.getLandNodeId())
                .orElseThrow(() -> new BizException(BizCodeEnum.INTERNAL_ERROR));
        // 落地节点只此一跳，没有 fallback 组可言：禁用即刻拒绝
        if (land.getStatus() != NodeStatus.ENABLED) {
            throw new BizException(BizCodeEnum.NODE_DISABLED);
        }
        if (land.getEgressIp() == null || land.getEgressIp().isBlank()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }
        return new RoutePart(resolveFrontGroups(user), land);
    }

    /** 只含线路部分的配置，专供算版本；凭据给空、ttl 给 0，二者本就不进哈希 */
    private static LinkConfigResponse routeOnly(RoutePart route) {
        return new LinkConfigResponse(route.frontGroups(), route.land().toMihomoNode(), route.land().getEgressIp(),
                route.land().getEgressTimezone(), List.of(), 0, null);
    }

    /**
     * 按用户第一跳订阅的顺位，每个订阅生成一组：组内只放落在当前地区的节点（FRONT 节点没有状态，不看 status）。
     * 某个订阅一个可用节点都没有就跳过（用户自然落到备用）；全部为空报 NODE_DISABLED——
     * 用户分配过，只是眼下所有订阅都没有节点，要做的是等订阅刷新或重新分配，而不是「去分配一个」。
     * 没有分配过报 EGRESS_NOT_ASSIGNED。
     */
    private List<LinkConfigResponse.FrontGroup> resolveFrontGroups(UserDto user) {
        List<Long> subscriptionIds = userFrontSubscriptionRepository.findSubscriptionIdsByUserId(user.getId());
        if (subscriptionIds.isEmpty()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        List<LinkConfigResponse.FrontGroup> groups = new ArrayList<>();
        for (Long subscriptionId : subscriptionIds) {
            SubscriptionRenderCache.RenderedSubscription rendered = renderCache.get(subscriptionId);
            if (rendered.nodes().isEmpty()) {
                continue;   // 该订阅眼下没有节点，用户自然落到备用
            }
            groups.add(new LinkConfigResponse.FrontGroup(rendered.failureDomain(), rendered.nodes()));
        }

        if (groups.isEmpty()) {
            throw new BizException(BizCodeEnum.NODE_DISABLED);
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
                        return new HeartbeatResponse(LinkStatus.SUSPENDED, null);
                    }
                    if (user.getStatus() == UserStatus.REVOKED) {
                        return new HeartbeatResponse(LinkStatus.REVOKED, null);
                    }
                    return new HeartbeatResponse(LinkStatus.ACTIVE, currentVersionOrNull(user));
                })
                .orElse(new HeartbeatResponse(LinkStatus.REVOKED, null));
    }

    /** 渲染不出配置（未分配、订阅无节点、无落地）就给 null：客户端看到 null 不动作，继续跑旧配置 */
    private String currentVersionOrNull(UserDto user) {
        try {
            return LinkConfigVersion.of(routeOnly(renderRoute(user)));
        } catch (BizException e) {
            return null;
        }
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
