package ai.mintpop.lane.repository;

import ai.mintpop.lane.enumeration.SettingKey;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("全局配置键值表")
class SystemSettingRepositoryTest extends MysqlTestBase {

    @Autowired private SystemSettingRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;

    @BeforeEach
    void setUp() {
        new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository).clearAll();
    }

    @Test
    @DisplayName("没有行时 findValue 为空；save 两次是先插入后更新，不重复建行")
    void saveIsUpsert() {
        assertThat(repository.findValue(SettingKey.FRONT_AIRPORTS_PER_USER)).isEmpty();

        repository.save(SettingKey.FRONT_AIRPORTS_PER_USER, "2");
        repository.save(SettingKey.FRONT_AIRPORTS_PER_USER, "4");

        assertThat(repository.findValue(SettingKey.FRONT_AIRPORTS_PER_USER)).contains("4");
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM system_setting WHERE setting_key = 'FRONT_AIRPORTS_PER_USER'", Integer.class);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("findAll 只返回表里有的键")
    void findAllReturnsOnlyStoredKeys() {
        repository.save(SettingKey.FRONT_REGION, "US");

        assertThat(repository.findAll()).containsOnlyKeys(SettingKey.FRONT_REGION);
    }
}
