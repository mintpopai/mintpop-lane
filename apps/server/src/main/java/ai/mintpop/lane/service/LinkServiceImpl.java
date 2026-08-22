package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.LinkStatus;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public LinkServiceImpl(LinkProperties linkProperties,
                           UserRepository userRepository,
                           ProxyNodeRepository nodeRepository,
                           SubscriptionRepository subscriptionRepository,
                           Clock clock) {
        this.linkProperties = linkProperties;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    public LinkConfigResponse resolveLink(Long userId) {
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

        // 只下发已录入凭据的在期订阅；凭据缺失只影响对应 agent 的会话，不拦建链——
        // 全部缺失也照常下发链路，客户端在会话入口单独提示
        Instant now = clock.instant();
        List<LinkConfigResponse.AgentCredential> credentials = subscriptionRepository
                .findByUserId(user.getId()).stream()
                .filter(s -> s.isActiveAt(now))
                .filter(s -> s.getCredential() != null && !s.getCredential().isBlank())
                .map(s -> new LinkConfigResponse.AgentCredential(
                        s.getId(), s.getName(), s.getAgentType(), s.getCredential(), s.getEndsAt()))
                .toList();

        return new LinkConfigResponse(
                front.toMihomoNode(),
                land.toMihomoNode(),
                land.getEgressIp(),
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
}
