package ai.mintpop.lane.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeRoleTest {

    @Test
    @DisplayName("落地节点只放行标准代理协议：服务端要经它出站，加密混淆协议连不上")
    void landAllowsOnlyStandardProxies() {
        assertThat(NodeRole.LAND.allows(NodeProtocol.SOCKS5)).isTrue();
        assertThat(NodeRole.LAND.allows(NodeProtocol.HTTP)).isTrue();
        assertThat(NodeRole.LAND.allows(NodeProtocol.TROJAN)).isFalse();
        assertThat(NodeRole.LAND.allows(NodeProtocol.VMESS)).isFalse();
        assertThat(NodeRole.LAND.allows(NodeProtocol.MIHOMO)).isFalse();
    }

    @Test
    @DisplayName("前置节点不受限：抗审查那一跳仍需要各类加密混淆协议")
    void frontAllowsEveryProtocol() {
        for (NodeProtocol protocol : NodeProtocol.values()) {
            assertThat(NodeRole.FRONT.allows(protocol)).isTrue();
        }
    }
}
