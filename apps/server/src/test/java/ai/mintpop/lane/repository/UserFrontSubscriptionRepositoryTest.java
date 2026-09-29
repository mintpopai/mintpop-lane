package ai.mintpop.lane.repository;

import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserFrontSubscriptionRepositoryTest extends MysqlTestBase {

    @Autowired private UserFrontSubscriptionRepository repository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
    }

    @Test
    @DisplayName("replaceAll：删光全表后按传入顺序批量写入，顺位 = 下标")
    void replaceAllRewritesWholeTable() {
        Long airportId = fixtures.createAirport("A");
        Long s1 = fixtures.createAirportSubscription(airportId, "A-01", 300);
        Long s2 = fixtures.createAirportSubscription(airportId, "A-02", 300);
        Long u1 = fixtures.createUser("u1", null);
        Long u2 = fixtures.createUser("u2", null);
        Long u3 = fixtures.createUser("u3", null);
        fixtures.assignFront(u3, s1);   // 旧数据，应被清掉

        Map<Long, List<Long>> plan = new LinkedHashMap<>();
        plan.put(u1, List.of(s1, s2));
        plan.put(u2, List.of(s2));
        repository.replaceAll(plan);

        assertThat(repository.findSubscriptionIdsByUserId(u1)).containsExactly(s1, s2);
        assertThat(repository.findSubscriptionIdsByUserId(u2)).containsExactly(s2);
        assertThat(repository.findSubscriptionIdsByUserId(u3)).isEmpty();
        assertThat(repository.countPrimaryByAirportSubscription()).containsOnly(Map.entry(s1, 1L), Map.entry(s2, 1L));
    }
}
