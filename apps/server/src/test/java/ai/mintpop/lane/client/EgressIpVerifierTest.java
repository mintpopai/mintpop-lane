package ai.mintpop.lane.client;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EgressIpVerifierTest {

    private ProxyNodeDto land(String egressIp) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setEgressIp(egressIp);
        return dto;
    }

    @Test
    @DisplayName("实际出口与登记的出口 IP 一致时通过")
    void passesWhenIpMatches() {
        EgressIpVerifier verifier = new EgressIpVerifier(node -> "203.0.113.7");
        assertThatCode(() -> verifier.verify(land("203.0.113.7"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("出口 IP 不符是配置错误，必须与探测失败区分开")
    void mismatchIsConfigError() {
        EgressIpVerifier verifier = new EgressIpVerifier(node -> "198.51.100.9");
        assertThatThrownBy(() -> verifier.verify(land("203.0.113.7")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_IP_MISMATCH);
    }

    @Test
    @DisplayName("探测本身失败是网络问题，报错要与 IP 不符不同，否则会把抖动误报成配置错误")
    void probeFailureIsNetworkError() {
        EgressIpVerifier verifier = new EgressIpVerifier(node -> {
            throw new RuntimeException("connect timed out");
        });
        assertThatThrownBy(() -> verifier.verify(land("203.0.113.7")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_PROBE_FAILED);
    }
}
