package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.NodeProbeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 落地节点连通性检测：经该节点探测实际出口 IP，与登记值比对。
 * 「不通」是检测结果而非请求错误，正常返回 reachable=false；只有节点不存在或非落地节点才报业务错。
 */
class AdminNodeProbeTest {

    private final ProxyNodeRepository nodeRepository = mock(ProxyNodeRepository.class);

    private AdminNodeServiceImpl serviceWith(EgressIpVerifier.EgressProbe probe) {
        return new AdminNodeServiceImpl(nodeRepository, mock(UserRepository.class),
                mock(UserFrontNodeRepository.class),
                mock(AirportSubscriptionRepository.class), probe, mock(NodeNotifyService.class));
    }

    private ProxyNodeDto node(NodeRole role, String egressIp) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setId(7L);
        dto.setRole(role);
        dto.setProtocol(role == NodeRole.LAND ? NodeProtocol.SOCKS5 : NodeProtocol.TROJAN);
        dto.setEgressIp(egressIp);
        when(nodeRepository.findById(7L)).thenReturn(Optional.of(dto));
        return dto;
    }

    @Test
    @DisplayName("实际出口与登记一致：连通、matched 为 true，并回传两侧 IP")
    void reportsMatchWhenEgressIpEquals() {
        node(NodeRole.LAND, "203.0.113.7");

        NodeProbeResponse result = serviceWith(land -> "203.0.113.7").probe(7L);

        assertThat(result.reachable()).isTrue();
        assertThat(result.actualEgressIp()).isEqualTo("203.0.113.7");
        assertThat(result.registeredEgressIp()).isEqualTo("203.0.113.7");
        assertThat(result.matched()).isTrue();
        assertThat(result.latencyMs()).isNotNegative();
        assertThat(result.error()).isNull();
    }

    @Test
    @DisplayName("实际出口与登记不一致：连通但 matched 为 false")
    void reportsMismatchWhenEgressIpDiffers() {
        node(NodeRole.LAND, "203.0.113.7");

        NodeProbeResponse result = serviceWith(land -> "198.51.100.9").probe(7L);

        assertThat(result.reachable()).isTrue();
        assertThat(result.actualEgressIp()).isEqualTo("198.51.100.9");
        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("登记的出口 IP 为空时无从比对，matched 为 null")
    void matchedIsNullWhenNothingRegistered() {
        node(NodeRole.LAND, null);

        NodeProbeResponse result = serviceWith(land -> "198.51.100.9").probe(7L);

        assertThat(result.reachable()).isTrue();
        assertThat(result.registeredEgressIp()).isNull();
        assertThat(result.matched()).isNull();
    }

    @Test
    @DisplayName("探测失败视为不通：正常返回 reachable=false 并附原因，不抛业务错")
    void unreachableIsAResultNotAnError() {
        node(NodeRole.LAND, "203.0.113.7");

        NodeProbeResponse result = serviceWith(land -> {
            throw new RuntimeException("connect timed out");
        }).probe(7L);

        assertThat(result.reachable()).isFalse();
        assertThat(result.actualEgressIp()).isNull();
        assertThat(result.matched()).isNull();
        assertThat(result.error()).contains("connect timed out");
    }

    @Test
    @DisplayName("前置节点走加密协议、服务端无内核连不了，检测只对落地节点开放")
    void rejectsFrontNode() {
        node(NodeRole.FRONT, null);

        assertThatThrownBy(() -> serviceWith(land -> "203.0.113.7").probe(7L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_PROBE_UNSUPPORTED);
    }

    @Test
    @DisplayName("节点不存在时报 NODE_NOT_FOUND")
    void rejectsUnknownNode() {
        when(nodeRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceWith(land -> "203.0.113.7").probe(7L))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_NOT_FOUND);
    }
}
