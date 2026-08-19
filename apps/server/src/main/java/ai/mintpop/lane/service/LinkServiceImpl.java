package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.HeartbeatResponse;
import ai.mintpop.lane.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;

    public LinkServiceImpl(LinkProperties linkProperties,
                           UserRepository userRepository,
                           ProxyNodeRepository nodeRepository) {
        this.linkProperties = linkProperties;
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
    }

    @Override
    public LinkConfigResponse resolveLink(String subject) {
        UserDto user = userRepository.findBySubject(subject)
                .orElseThrow(() -> new BizException(BizCodeEnum.ACCOUNT_NOT_ENROLLED));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.LINK_REVOKED);
        }

        // 没有落地出口，客户端就无法做出口校验，等于放弃 fail-closed 的第二道闸
        if (user.getLandNodeId() == null) {
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

        if (user.getClaudeCredential() == null || user.getClaudeCredential().isBlank()) {
            throw new BizException(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
        }

        return new LinkConfigResponse(
                front.toMihomoNode(),
                land.toMihomoNode(),
                land.getEgressIps(),
                user.getClaudeCredential(),
                linkProperties.getTtlSeconds()
        );
    }

    @Override
    public HeartbeatResponse heartbeat(String subject) {
        // 从库里消失等价于权限被收回，按吊销处理让客户端断链
        UserStatus status = userRepository.findBySubject(subject)
                .map(UserDto::getStatus)
                .orElse(UserStatus.REVOKED);

        return new HeartbeatResponse(status);
    }
}
