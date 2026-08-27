package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.exception.BizException;
import org.springframework.stereotype.Component;

/**
 * 签发前置守卫。判定按「越便宜越根本越靠前」排序，任一不过即中止。
 *
 * 顺序是硬约束：必须先判出口 IP 是否已登记（{@link EgressIpVerifier#verify}
 * 内部直接对 egressIp 调 equals，自身不防 null），这一步就是它的 null 兜底，
 * 调换顺序会让未填出口 IP 的落地节点在 verify 里抛 NPE 而不是清楚的业务错误。
 * 最后一步实探出口，是本设计相对纯配置校验的净增保障。
 */
@Component
public class CredentialIssueGuard {

    private final EgressIpVerifier egressIpVerifier;

    public CredentialIssueGuard(EgressIpVerifier egressIpVerifier) {
        this.egressIpVerifier = egressIpVerifier;
    }

    public void check(SubscriptionDto subscription, UserDto user, ProxyNodeDto front, ProxyNodeDto land) {
        if (subscription.getAgentType() != AgentType.CLAUDE) {
            throw new BizException(BizCodeEnum.CREDENTIAL_ISSUE_NOT_SUPPORTED);
        }
        if (subscription.getUserId() == null) {
            throw new BizException(BizCodeEnum.LINK_NOT_READY_FOR_ISSUE);
        }
        if (user.getFrontNodeId() == null || user.getLandNodeId() == null || front == null || land == null) {
            throw new BizException(BizCodeEnum.LINK_NOT_READY_FOR_ISSUE);
        }
        if (front.getStatus() != NodeStatus.ENABLED || land.getStatus() != NodeStatus.ENABLED) {
            throw new BizException(BizCodeEnum.NODE_DISABLED);
        }
        if (!NodeRole.LAND.allows(land.getProtocol())) {
            throw new BizException(BizCodeEnum.NODE_PROTOCOL_NOT_ALLOWED);
        }
        if (land.getEgressIp() == null || land.getEgressIp().isBlank()) {
            throw new BizException(BizCodeEnum.EGRESS_NOT_ASSIGNED);
        }
        // 实探：配置说的出口与实际出口必须是同一个，否则签出的凭证会从别处使用
        egressIpVerifier.verify(land);
    }
}
