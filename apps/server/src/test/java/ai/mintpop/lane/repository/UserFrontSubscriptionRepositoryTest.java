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
import java.util.Set;

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
        repository.replaceAll(plan, Set.of());

        assertThat(repository.findSubscriptionIdsByUserId(u1)).containsExactly(s1, s2);
        assertThat(repository.findSubscriptionIdsByUserId(u2)).containsExactly(s2);
        assertThat(repository.findSubscriptionIdsByUserId(u3)).isEmpty();
        assertThat(repository.countPrimaryByAirportSubscription()).containsOnly(Map.entry(s1, 1L), Map.entry(s2, 1L));
    }

    @Test
    @DisplayName("replaceAll 保留 keepUserIds 的行与手动标记，其余重写为自动")
    void replaceAllKeepsGivenUsers() {
        Long airportId = fixtures.createAirport("A");
        Long s1 = fixtures.createAirportSubscription(airportId, "A-01", 300);
        Long s2 = fixtures.createAirportSubscription(airportId, "A-02", 300);
        Long manualUser = fixtures.createUser("m", null);
        Long autoUser = fixtures.createUser("a", null);
        fixtures.assignFrontManually(manualUser, s2);
        fixtures.assignFrontManually(autoUser, s1);   // 不在保留名单里：被重写，手动标记随之清掉

        repository.replaceAll(Map.of(autoUser, List.of(s2)), Set.of(manualUser));

        assertThat(repository.findSubscriptionIdsByUserId(manualUser)).containsExactly(s2);
        assertThat(repository.findSubscriptionIdsByUserId(autoUser)).containsExactly(s2);
        assertThat(repository.findManualUserIds()).containsExactly(manualUser);
    }

    @Test
    @DisplayName("replaceForUser 按 manual 写标记；改回自动后不再是手动用户")
    void replaceForUserWritesManualFlag() {
        Long airportId = fixtures.createAirport("A");
        Long s1 = fixtures.createAirportSubscription(airportId, "A-01", 300);
        Long u = fixtures.createUser("u", null);

        repository.replaceForUser(u, List.of(s1), true);
        assertThat(repository.findManualUserIds()).containsExactly(u);

        repository.replaceForUser(u, List.of(s1), false);
        assertThat(repository.findManualUserIds()).isEmpty();
    }
}
