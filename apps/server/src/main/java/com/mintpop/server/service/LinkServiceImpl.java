package com.mintpop.server.service;

import com.mintpop.server.config.LinkProperties;
import com.mintpop.server.entity.User;
import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.enumeration.UserStatus;
import com.mintpop.server.exception.BizException;
import com.mintpop.server.repository.UserRepository;
import com.mintpop.server.response.HeartbeatResponse;
import com.mintpop.server.response.LinkConfigResponse;
import org.springframework.stereotype.Service;

@Service
public class LinkServiceImpl implements LinkService {

    private final LinkProperties linkProperties;
    private final UserRepository userRepository;

    public LinkServiceImpl(LinkProperties linkProperties, UserRepository userRepository) {
        this.linkProperties = linkProperties;
        this.userRepository = userRepository;
    }

    @Override
    public LinkConfigResponse resolveLink(String subject) {
        User user = userRepository.findBySubject(subject)
                .orElseThrow(() -> new BizException(BizCodeEnum.ACCOUNT_NOT_ENROLLED));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.LINK_REVOKED);
        }

        // 没有期望出口，客户端就无法做出口校验，等于放弃 fail-closed 的第二道闸
        if (user.getExpectedEgressIps() == null || user.getExpectedEgressIps().isEmpty()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }

        if (user.getClaudeCredential() == null || user.getClaudeCredential().isBlank()) {
            throw new BizException(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
        }

        return new LinkConfigResponse(
                linkProperties.getFront(),
                user.getLand(),
                user.getExpectedEgressIps(),
                user.getClaudeCredential(),
                linkProperties.getTtlSeconds()
        );
    }

    @Override
    public HeartbeatResponse heartbeat(String subject) {
        // 从绑定表中消失等价于权限被收回，按吊销处理让客户端断链
        UserStatus status = userRepository.findBySubject(subject)
                .map(User::getStatus)
                .orElse(UserStatus.REVOKED);

        return new HeartbeatResponse(status);
    }
}
