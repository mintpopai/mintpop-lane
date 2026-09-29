package ai.mintpop.lane.service;

import ai.mintpop.lane.response.LinkConfigResponse;
import ai.mintpop.lane.response.LinkConfigResponse.FrontGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("configVersion：只随客户端会跑的配置内容变化")
class LinkConfigVersionTest {

    private static Map<String, Object> node(String server, int port, String password) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "anytls");
        m.put("server", server);
        m.put("port", port);
        m.put("password", password);
        m.put("alpn", List.of("h2", "http/1.1"));
        return m;
    }

    private static LinkConfigResponse config(List<FrontGroup> groups, Map<String, Object> land, String egressIp, String tz) {
        return new LinkConfigResponse(groups, land, egressIp, tz, List.of(), 900, null);
    }

    private static final Map<String, Object> LAND = Map.of("type", "socks5", "server", "land.example.com", "port", 1080);

    @Test
    @DisplayName("同一份内容、键顺序不同、节点顺序不同，版本相同")
    void sameContentDifferentKeyOrderHasSameVersion() {
        Map<String, Object> a = node("us01a.t11-a.app", 35660, "p1");
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("alpn", List.of("h2", "http/1.1"));
        reordered.put("password", "p1");
        reordered.put("port", 35660);
        reordered.put("server", "us01a.t11-a.app");
        reordered.put("type", "anytls");
        Map<String, Object> b = node("us02a.t11-a.app", 35661, "p1");

        String v1 = LinkConfigVersion.of(config(List.of(new FrontGroup("d1", List.of(a, b))), LAND, "1.2.3.4", "America/Los_Angeles"));
        String v2 = LinkConfigVersion.of(config(List.of(new FrontGroup("d1", List.of(b, reordered))), LAND, "1.2.3.4", "America/Los_Angeles"));

        assertThat(v1).isEqualTo(v2).hasSize(64);
    }

    @Test
    @DisplayName("密码变、端口变、节点增删、组顺位变、落地变、出口 IP 变、时区变：版本都变")
    void changesThatMatterChangeVersion() {
        Map<String, Object> a = node("us01a.t11-a.app", 35660, "p1");
        Map<String, Object> b = node("us02a.t11-a.app", 35661, "p1");
        FrontGroup g1 = new FrontGroup("d1", List.of(a));
        FrontGroup g2 = new FrontGroup("d2", List.of(b));
        String base = LinkConfigVersion.of(config(List.of(g1, g2), LAND, "1.2.3.4", "America/Los_Angeles"));

        assertThat(LinkConfigVersion.of(config(List.of(new FrontGroup("d1", List.of(node("us01a.t11-a.app", 35660, "p2"))), g2), LAND, "1.2.3.4", "America/Los_Angeles"))).isNotEqualTo(base);
        assertThat(LinkConfigVersion.of(config(List.of(new FrontGroup("d1", List.of(node("us01a.t11-a.app", 35661, "p1"))), g2), LAND, "1.2.3.4", "America/Los_Angeles"))).isNotEqualTo(base);
        assertThat(LinkConfigVersion.of(config(List.of(new FrontGroup("d1", List.of(a, b)), g2), LAND, "1.2.3.4", "America/Los_Angeles"))).isNotEqualTo(base);
        assertThat(LinkConfigVersion.of(config(List.of(g2, g1), LAND, "1.2.3.4", "America/Los_Angeles"))).isNotEqualTo(base);
        assertThat(LinkConfigVersion.of(config(List.of(g1, g2), Map.of("type", "socks5", "server", "other", "port", 1080), "1.2.3.4", "America/Los_Angeles"))).isNotEqualTo(base);
        assertThat(LinkConfigVersion.of(config(List.of(g1, g2), LAND, "5.6.7.8", "America/Los_Angeles"))).isNotEqualTo(base);
        assertThat(LinkConfigVersion.of(config(List.of(g1, g2), LAND, "1.2.3.4", "America/New_York"))).isNotEqualTo(base);
    }

    @Test
    @DisplayName("failureDomain、席位凭据、ttl、configVersion 自身不进哈希")
    void ignoredFieldsDoNotChangeVersion() {
        FrontGroup g = new FrontGroup("d1", List.of(node("us01a.t11-a.app", 35660, "p1")));
        LinkConfigResponse base = config(List.of(g), LAND, "1.2.3.4", null);
        String v = LinkConfigVersion.of(base);

        assertThat(LinkConfigVersion.of(config(List.of(new FrontGroup("other-domain", g.nodes())), LAND, "1.2.3.4", null))).isEqualTo(v);
        assertThat(LinkConfigVersion.of(new LinkConfigResponse(List.of(g), LAND, "1.2.3.4", null, List.of(), 1, "whatever"))).isEqualTo(v);
    }

    @Test
    @DisplayName("组内重复节点按集合去重")
    void duplicateNodesCollapse() {
        Map<String, Object> a = node("us01a.t11-a.app", 35660, "p1");
        assertThat(LinkConfigVersion.of(config(List.of(new FrontGroup("d", List.of(a, a))), LAND, "1.2.3.4", null)))
                .isEqualTo(LinkConfigVersion.of(config(List.of(new FrontGroup("d", List.of(a))), LAND, "1.2.3.4", null)));
    }
}
