package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
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
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, repository, userRepository, subscriptionRepository);
        fixtures.clearAll();
    }

    @Test
    @DisplayName("节点存取往返：JSON 列与敏感键都能原样取回")
    void nodeRoundTrip() {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName("LAND-东京-03");
        node.setRole(NodeRole.LAND);
        node.setProtocol(NodeProtocol.SOCKS5);
        node.setServerAddr("203.0.113.10");
        node.setPort(50101);
        node.setExtraConfig(Map.of("udp", true));
        node.setSecret(Map.of("username", "落地用户名", "password", "落地密码"));
        node.setEgressIp("203.0.113.10");
        node.setRemark("给张三用");

        Long id = repository.create(node);
        ProxyNodeDto loaded = repository.findById(id).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("LAND-东京-03");
        assertThat(loaded.getRole()).isEqualTo(NodeRole.LAND);
        assertThat(loaded.getProtocol()).isEqualTo(NodeProtocol.SOCKS5);
        assertThat(loaded.getPort()).isEqualTo(50101);
        assertThat(loaded.getExtraConfig()).containsEntry("udp", true);
        assertThat(loaded.getSecret()).containsEntry("password", "落地密码");
        assertThat(loaded.getEgressIp()).isEqualTo("203.0.113.10");
        assertThat(loaded.getStatus()).isEqualTo(NodeStatus.ENABLED);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("敏感键在库里是密文，明文一个字符都不落库")
    void secretKeysStoredAsCipher() {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");

        String stored = fixtures.readRawCipherColumn("proxy_node", "secret_cipher", id);

        // 断言用的明文必须含中文：stored 是 AES-GCM 密文的 Base64，字母表只有 ASCII，
        // 若拿纯 ASCII 短串（如曾经的 "u1"）去断言「不包含」，会有一定概率在随机密文里
        // 偶然撞中而误报——这与加密是否正确无关，是断言本身的缺陷。中文在 Base64 里
        // 不可能出现，断言才是恒定有效的。后人改动这两个夹具值时，务必保留中文。
        assertThat(stored).isNotBlank().doesNotContain("land-密码").doesNotContain("land-用户名");
    }

    @Test
    @DisplayName("按角色过滤节点，传 null 时返回全部")
    void filterNodesByRole() {
        fixtures.createFrontNode("FRONT-1");
        fixtures.createLandNode("LAND-1", "203.0.113.10");
        fixtures.createLandNode("LAND-2", "203.0.113.11");

        assertThat(repository.findAll(NodeRole.FRONT)).hasSize(1);
        assertThat(repository.findAll(NodeRole.LAND)).hasSize(2);
        assertThat(repository.findAll(null)).hasSize(3);
    }

    @Test
    @DisplayName("更新节点：改敏感键与出口 IP 都能生效")
    void updateNode() {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");
        ProxyNodeDto node = repository.findById(id).orElseThrow();
        node.setSecret(Map.of("username", "u2", "password", "换过的密码"));
        node.setEgressIp("198.51.100.7");
        node.setStatus(NodeStatus.DISABLED);

        repository.update(node);
        ProxyNodeDto reloaded = repository.findById(id).orElseThrow();

        assertThat(reloaded.getSecret()).containsEntry("password", "换过的密码");
        assertThat(reloaded.getEgressIp()).isEqualTo("198.51.100.7");
        assertThat(reloaded.getStatus()).isEqualTo(NodeStatus.DISABLED);
    }

    @Test
    @DisplayName("更新节点：出口 IP 置 null 能清空——MyBatis-Plus 默认跳过 null 字段，此处必须能覆盖")
    void updateNodeClearsEgressIp() {
        Long id = fixtures.createLandNode("LAND-1", "203.0.113.10");
        ProxyNodeDto node = repository.findById(id).orElseThrow();
        node.setEgressIp(null);

        repository.update(node);

        assertThat(repository.findById(id).orElseThrow().getEgressIp()).isNull();
    }

    @Test
    @DisplayName("节点名重复能被查出来，且删除后即释放")
    void duplicateNameAndDelete() {
        Long id = fixtures.createFrontNode("FRONT-1");

        assertThat(repository.existsByName("FRONT-1")).isTrue();
        assertThat(repository.existsByName("不存在的名字")).isFalse();

        repository.deleteById(id);

        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.existsByName("FRONT-1")).isFalse();
    }

    @Test
    @DisplayName("组装成 mihomo 节点：type 取小写协议名，敏感键并入同一层")
    void assembleMihomoNode() {
        Long id = fixtures.createFrontNode("FRONT-1");

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

    @Test
    @DisplayName("MIHOMO 节点整份参数加密落库，读回明文一致，来源三字段原样往返")
    void mihomoNodeFullParamsCipherRoundTrip() {
        Long groupId = createGroup();   // 直接 jdbc 插一条 node_group，见下
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName("香港 IEPL-01");
        node.setRole(NodeRole.FRONT);
        node.setProtocol(NodeProtocol.MIHOMO);
        node.setServerAddr("hk02a.example.com");
        node.setPort(35356);
        node.setSecret(Map.of("type", "anytls", "server", "hk02a.example.com",
                "port", 35356, "password", "uuid-秘密"));
        node.setGroupId(groupId);
        node.setSourceName("香港 IEPL-01");
        node.setSourceType("anytls");
        Long id = repository.create(node);

        ProxyNodeDto loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getSecret()).containsEntry("password", "uuid-秘密").containsEntry("type", "anytls");
        assertThat(loaded.getGroupId()).isEqualTo(groupId);
        assertThat(loaded.getSourceName()).isEqualTo("香港 IEPL-01");
        assertThat(loaded.getSourceType()).isEqualTo("anytls");
        // 库里存的是密文，不是明文参数
        assertThat(fixtures.readRawCipherColumn("proxy_node", "secret_cipher", id)).doesNotContain("uuid-秘密");
        // 下发形态：整份 secret 原样返回，不混入列上的字段
        assertThat(loaded.toMihomoNode()).isEqualTo(loaded.getSecret());
    }

    private Long createGroup() {
        jdbc.update("INSERT INTO node_group (name, sub_url_cipher) VALUES ('测试组', '密文占位')");
        return jdbc.queryForObject("SELECT id FROM node_group WHERE name = '测试组'", Long.class);
    }
}
