package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubscriptionRepository subscriptionRepository;

    public LinkServiceImpl(LinkProperties linkProperties,
                           UserRepository userRepository,
                           ProxyNodeRepository nodeRepository,
                           SubscriptionRepository subscriptionRepository) {
        this.linkProperties = linkProperties;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public LinkConfigResponse resolveLink(String subject) {
        UserDto user = userRepository.findBySubject(subject)
                .orElseThrow(() -> new BizException(BizCodeEnum.LINK_REVOKED));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.LINK_REVOKED);
        }

        // 服务权益判定全部来自订阅：没买过与买过但全过期给不同文案，其余一律 fail-closed
        List<SubscriptionDto> subscriptions = subscriptionRepository.findByUserId(user.getId());
        if (subscriptions.isEmpty()) {
            throw new BizException(BizCodeEnum.SERVICE_NOT_PURCHASED);
        }
        LocalDateTime now = LocalDateTime.now();
        List<SubscriptionDto> active = subscriptions.stream()
                .filter(s -> s.isActiveAt(now))
                .toList();
        if (active.isEmpty()) {
            throw new BizException(BizCodeEnum.SERVICE_EXPIRED);
        }

        // 有在期订阅却没分配链路资源，属于开通动作没做完
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

        if (land.getEgressIps() == null || land.getEgressIps().isEmpty()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        // 只下发已录入凭据的在期订阅；没凭据的 agent 单独不可用，不拖累整条链路
        List<LinkConfigResponse.AgentCredential> credentials = active.stream()
                .filter(s -> s.getCredential() != null && !s.getCredential().isBlank())
                .map(s -> new LinkConfigResponse.AgentCredential(
                        s.getId(), s.getName(), s.getAgentType(), s.getCredential(), s.getEndsAt()))
                .toList();
        if (credentials.isEmpty()) {
            throw new BizException(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
        }

        return new LinkConfigResponse(
                front.toMihomoNode(),
                land.toMihomoNode(),
                land.getEgressIps(),
                credentials,
                linkProperties.getTtlSeconds()
        );
    }

    @Override
    public HeartbeatResponse heartbeat(String subject) {
        // 从库里消失等价于权限被收回，按吊销处理让客户端断链
        return userRepository.findBySubject(subject)
                .map(user -> {
                    if (user.getStatus() == UserStatus.SUSPENDED) {
                        return new HeartbeatResponse(LinkStatus.SUSPENDED);
                    }
                    if (user.getStatus() == UserStatus.REVOKED) {
                        return new HeartbeatResponse(LinkStatus.REVOKED);
                    }
                    boolean anyActive = subscriptionRepository.findByUserId(user.getId()).stream()
                            .anyMatch(s -> s.isActiveAt(LocalDateTime.now()));
                    return new HeartbeatResponse(anyActive ? LinkStatus.ACTIVE : LinkStatus.EXPIRED);
                })
                .orElse(new HeartbeatResponse(LinkStatus.REVOKED));
    }
}
