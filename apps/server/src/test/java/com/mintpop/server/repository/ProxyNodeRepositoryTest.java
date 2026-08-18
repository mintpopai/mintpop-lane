package com.mintpop.server.repository;

import com.mintpop.server.dto.ProxyNodeDto;
import com.mintpop.server.enumeration.NodeProtocol;
import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.enumeration.NodeStatus;
import com.mintpop.server.support.DatabaseFixtures;
import com.mintpop.server.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyNodeRepositoryTest extends MysqlTestBase {

    @Autowired
    private ProxyNodeRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private DatabaseFixtures fixtures;

    @BeforeEach
    void 准备() {
        fixtures = new DatabaseFixtures(jdbc, repository);
        fixtures.清空();
    }

    @Test
    @DisplayName("节点存取往返：JSON 列与敏感键都能原样取回")
    void 节点存取往返() {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName("LAND-东京-03");
        node.setRole(NodeRole.LAND);
        node.setProtocol(NodeProtocol.SOCKS5);
        node.setServerAddr("203.0.113.10");
        node.setPort(50101);
        node.setExtraConfig(Map.of("udp", true));
        node.setSecret(Map.of("username", "u1", "password", "落地密码"));
        node.setEgressIps(List.of("203.0.113.10", "203.0.113.11"));
        node.setRemark("给张三用");

        Long id = repository.create(node);
        ProxyNodeDto loaded = repository.findById(id).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("LAND-东京-03");
        assertThat(loaded.getRole()).isEqualTo(NodeRole.LAND);
        assertThat(loaded.getProtocol()).isEqualTo(NodeProtocol.SOCKS5);
        assertThat(loaded.getPort()).isEqualTo(50101);
        assertThat(loaded.getExtraConfig()).containsEntry("udp", true);
        assertThat(loaded.getSecret()).containsEntry("password", "落地密码");
        assertThat(loaded.getEgressIps()).containsExactly("203.0.113.10", "203.0.113.11");
        assertThat(loaded.getStatus()).isEqualTo(NodeStatus.ENABLED);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("敏感键在库里是密文，明文一个字符都不落库")
    void 敏感键在库里是密文() {
        Long id = fixtures.建LAND节点("LAND-1", "203.0.113.10");

        String stored = fixtures.读原始密文列("proxy_node", "secret_cipher", id);

        assertThat(stored).isNotBlank().doesNotContain("land-密码").doesNotContain("u1");
    }

    @Test
    @DisplayName("按角色过滤节点，传 null 时返回全部")
    void 按角色过滤节点() {
        fixtures.建FRONT节点("FRONT-1");
        fixtures.建LAND节点("LAND-1", "203.0.113.10");
        fixtures.建LAND节点("LAND-2", "203.0.113.11");

        assertThat(repository.findAll(NodeRole.FRONT)).hasSize(1);
        assertThat(repository.findAll(NodeRole.LAND)).hasSize(2);
        assertThat(repository.findAll(null)).hasSize(3);
    }

    @Test
    @DisplayName("更新节点：改敏感键与出口 IP 都能生效")
    void 更新节点() {
        Long id = fixtures.建LAND节点("LAND-1", "203.0.113.10");
        ProxyNodeDto node = repository.findById(id).orElseThrow();
        node.setSecret(Map.of("username", "u2", "password", "换过的密码"));
        node.setEgressIps(List.of("198.51.100.7"));
        node.setStatus(NodeStatus.DISABLED);

        repository.update(node);
        ProxyNodeDto reloaded = repository.findById(id).orElseThrow();

        assertThat(reloaded.getSecret()).containsEntry("password", "换过的密码");
        assertThat(reloaded.getEgressIps()).containsExactly("198.51.100.7");
        assertThat(reloaded.getStatus()).isEqualTo(NodeStatus.DISABLED);
    }

    @Test
    @DisplayName("节点名重复能被查出来，且删除后即释放")
    void 节点名重复与删除() {
        Long id = fixtures.建FRONT节点("FRONT-1");

        assertThat(repository.existsByName("FRONT-1")).isTrue();
        assertThat(repository.existsByName("不存在的名字")).isFalse();

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsByName("FRONT-1")).isFalse();
    }

    @Test
    @DisplayName("组装成 mihomo 节点：type 取小写协议名，敏感键并入同一层")
    void 组装成mihomo节点() {
        Long id = fixtures.建FRONT节点("FRONT-1");

        Map<String, Object> mihomo = repository.findById(id).orElseThrow().toMihomoNode();

        assertThat(mihomo)
                .containsEntry("type", "trojan")
                .containsEntry("server", "us.example.com")
                .containsEntry("port", 443)
                .containsEntry("sni", "us.example.com")
                .containsEntry("password", "front-密码");
        // 节点名与 dialer-proxy 由客户端强制覆盖，服务端不下发
        assertThat(mihomo).doesNotContainKeys("name", "dialer-proxy");
    }
}
