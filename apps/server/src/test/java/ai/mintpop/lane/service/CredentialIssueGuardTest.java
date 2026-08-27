package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class CredentialIssueGuardTest {

    private final EgressIpVerifier verifier = mock(EgressIpVerifier.class);
    private final CredentialIssueGuard guard = new CredentialIssueGuard(verifier);

    private SubscriptionDto claudeSubscription() {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setAgentType(AgentType.CLAUDE);
        dto.setUserId(1L);
        return dto;
    }

    private UserDto linkedUser() {
        UserDto user = new UserDto();
        user.setFrontNodeId(10L);
        user.setLandNodeId(20L);
        return user;
    }

    private ProxyNodeDto node(NodeProtocol protocol, String egressIp) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setProtocol(protocol);
        dto.setStatus(NodeStatus.ENABLED);
        dto.setEgressIp(egressIp);
        return dto;
    }

    @Test
    @DisplayName("链路完整、协议合规、出口相符时放行")
    void passesWhenEverythingReady() {
        doNothing().when(verifier).verify(org.mockito.ArgumentMatchers.any());
        assertThatCode(() -> guard.check(claudeSubscription(), linkedUser(),
                node(NodeProtocol.TROJAN, null), node(NodeProtocol.HTTP, "203.0.113.7")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("链路未配置完整不得签发：没有确定的出口，签出的凭证即来路不明")
    void rejectsIncompleteLink() {
        UserDto user = new UserDto();
        user.setFrontNodeId(10L);
        assertThatThrownBy(() -> guard.check(claudeSubscription(), user, null, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_NOT_READY_FOR_ISSUE);
    }

    @Test
    @DisplayName("非 Claude 席位没有这条签发流程")
    void rejectsNonClaudeSeat() {
        SubscriptionDto dto = claudeSubscription();
        dto.setAgentType(AgentType.CODEX);
        assertThatThrownBy(() -> guard.check(dto, linkedUser(),
                node(NodeProtocol.TROJAN, null), node(NodeProtocol.HTTP, "203.0.113.7")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_ISSUE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("落地节点未登记出口 IP 时无法校验一致性，拒绝签发")
    void rejectsMissingEgressIp() {
        assertThatThrownBy(() -> guard.check(claudeSubscription(), linkedUser(),
                node(NodeProtocol.TROJAN, null), node(NodeProtocol.HTTP, null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }
}
