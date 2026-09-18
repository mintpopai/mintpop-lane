package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.NodeProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("下发节点时按协议注入保活参数覆盖")
class ProxyNodeDtoTest {

    @Test
    @DisplayName("覆盖表里有该协议时，参数被合并进下发配置")
    void appliesTuningForKnownProtocol() {
        ProxyNodeDto node = mihomoNode("anytls", Map.of("type", "anytls", "server", "a.example.com"));
        Map<String, Object> out = node.toMihomoNode(Map.of("idle-session-timeout", 300, "min-idle-session", 1));
        assertThat(out).containsEntry("idle-session-timeout", 300).containsEntry("min-idle-session", 1);
        assertThat(out).containsEntry("server", "a.example.com");
    }

    @Test
    @DisplayName("覆盖表为空时原样透传，一个键都不加")
    void passesThroughWhenNoTuning() {
        ProxyNodeDto node = mihomoNode("hysteria2", Map.of("type", "hysteria2", "server", "b.example.com"));
        assertThat(node.toMihomoNode(Map.of())).containsOnlyKeys("type", "server");
    }

    @Test
    @DisplayName("订阅自带同名键时以覆盖表为准")
    void tuningWinsOverSubscriptionValue() {
        ProxyNodeDto node = mihomoNode("anytls",
                Map.of("type", "anytls", "idle-session-timeout", 30));
        assertThat(node.toMihomoNode(Map.of("idle-session-timeout", 300)))
                .containsEntry("idle-session-timeout", 300);
    }

    @Test
    @DisplayName("非 MIHOMO 协议的节点不受覆盖表影响")
    void doesNotTouchNonMihomoNodes() {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setProtocol(NodeProtocol.SOCKS5);
        node.setServerAddr("land.example.com");
        node.setPort(1080);
        node.setSecret(Map.of("username", "u", "password", "p"));

        Map<String, Object> out = node.toMihomoNode(Map.of("min-idle-session", 1));

        assertThat(out).doesNotContainKey("min-idle-session");
        assertThat(out).containsEntry("server", "land.example.com");
    }

    /** 造一个订阅导入形态的节点：整份参数都在 secret 里 */
    private ProxyNodeDto mihomoNode(String sourceType, Map<String, Object> params) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setProtocol(NodeProtocol.MIHOMO);
        node.setSourceType(sourceType);
        node.setSecret(params);
        return node;
    }
}
