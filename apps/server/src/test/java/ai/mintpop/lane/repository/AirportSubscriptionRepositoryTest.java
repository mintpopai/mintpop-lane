package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AirportSubscriptionRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AirportSubscriptionRepository airportSubscriptionRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;

    private Long airportId;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        airportId = fixtures.createAirport("泰山云");
    }

    private AirportSubscriptionDto newGroup(String name) {
        AirportSubscriptionDto group = new AirportSubscriptionDto();
        group.setAirportId(airportId);
        group.setName(name);
        group.setAccount(name + "@airport.example");
        group.setBandwidthMbps(300);
        group.setSubUrl("https://sub.example.com/c?token=秘密token");
        group.setRemark("测试用");
        return group;
    }

    @Test
    @DisplayName("订阅创建后读回明文一致，订阅链接在库里是密文")
    void createReadsBackPlainAndStoresCipher() {
        Long id = airportSubscriptionRepository.create(newGroup("机场A"));

        AirportSubscriptionDto loaded = airportSubscriptionRepository.findById(id).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("机场A");
        assertThat(loaded.getSubUrl()).isEqualTo("https://sub.example.com/c?token=秘密token");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(fixtures.readRawCipherColumn("airport_subscription", "sub_url_cipher", id)).doesNotContain("秘密token");
    }

    @Test
    @DisplayName("existsByName 与改名更新")
    void existsByNameAndRename() {
        Long id = airportSubscriptionRepository.create(newGroup("机场A"));
        assertThat(airportSubscriptionRepository.existsByName("机场A")).isTrue();
        assertThat(airportSubscriptionRepository.existsByName("机场B")).isFalse();

        AirportSubscriptionDto group = airportSubscriptionRepository.findById(id).orElseThrow();
        group.setName("机场B");
        airportSubscriptionRepository.update(group);
        assertThat(airportSubscriptionRepository.findById(id).orElseThrow().getName()).isEqualTo("机场B");
    }

    @Test
    @DisplayName("列表按 id 升序，删除后消失")
    void listAndDelete() {
        Long a = airportSubscriptionRepository.create(newGroup("机场A"));
        airportSubscriptionRepository.create(newGroup("机场B"));
        assertThat(airportSubscriptionRepository.findAll()).hasSize(2);
        assertThat(airportSubscriptionRepository.findAll().get(0).getId()).isEqualTo(a);

        airportSubscriptionRepository.deleteById(a);
        assertThat(airportSubscriptionRepository.findAll()).hasSize(1);
        assertThat(airportSubscriptionRepository.findById(a)).isEmpty();
    }

    @Test
    @DisplayName("按订阅与来源名查节点、按订阅计数与列表")
    void findNodesByGroup() {
        Long airportSubscriptionId = airportSubscriptionRepository.create(newGroup("机场A"));
        fixtures.createMihomoNode("香港-01", airportSubscriptionId);
        fixtures.createMihomoNode("香港-02", airportSubscriptionId);
        fixtures.createFrontNode("手工节点");

        assertThat(nodeRepository.countByAirportSubscriptionId(airportSubscriptionId)).isEqualTo(2);
        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)).hasSize(2);
        assertThat(nodeRepository.findByAirportSubscriptionIdAndSourceName(airportSubscriptionId, "香港-01")).isPresent();
        assertThat(nodeRepository.findByAirportSubscriptionIdAndSourceName(airportSubscriptionId, "不存在")).isEmpty();
    }

    @Test
    @DisplayName("拉取失败状态写入后能清回 NULL（updateStrategy = ALWAYS，否则 MyBatis-Plus 会跳过 null）")
    void clearingFetchFailurePersistsNull() {
        Long id = fixtures.createAirportSubscription(airportId, "泰山-01", 300);

        AirportSubscriptionDto dto = airportSubscriptionRepository.findById(id).orElseThrow();
        dto.setFetchFailedSince(Instant.parse("2026-09-29T01:00:00Z"));
        dto.setLastFetchError("订阅拉取失败：链接无法访问或返回错误");
        airportSubscriptionRepository.update(dto);
        assertThat(airportSubscriptionRepository.findById(id).orElseThrow().getFetchFailedSince()).isEqualTo(Instant.parse("2026-09-29T01:00:00Z"));

        dto = airportSubscriptionRepository.findById(id).orElseThrow();
        dto.setFetchFailedSince(null);
        dto.setLastFetchError(null);
        airportSubscriptionRepository.update(dto);

        AirportSubscriptionDto cleared = airportSubscriptionRepository.findById(id).orElseThrow();
        assertThat(cleared.getFetchFailedSince()).isNull();
        assertThat(cleared.getLastFetchError()).isNull();
    }
}
