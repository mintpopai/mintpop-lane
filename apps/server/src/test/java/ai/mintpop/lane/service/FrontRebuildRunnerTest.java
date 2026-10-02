package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.*;
import ai.mintpop.lane.service.FrontRebuildRunner.RebuildResult;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("全体重算的事务部分：预检、从零重排、批量写回")
class FrontRebuildRunnerTest extends MysqlTestBase {

    @Autowired private FrontRebuildRunner runner;
    @Autowired private UserFrontSubscriptionRepository userFrontSubscriptionRepository;
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

    private Long usableSubscription(String airport, int bandwidth) {
        Long airportId = fixtures.createAirport(airport);
        Long subId = fixtures.createAirportSubscription(airportId, airport + "-01", bandwidth);
        fixtures.createSubscriptionNode(subId, "🇺🇸[US]" + airport + "-01", NodeStatus.ENABLED);
        return subId;
    }

    private Long activeUser(String subject) {
        Long id = fixtures.createUser(subject, null);
        Instant now = Instant.now();
        fixtures.createSubscription(id, AgentType.CLAUDE, "月付", now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(29)), null);
        return id;
    }

    @Test
    @DisplayName("三家 300M、四个激活用户：每人三项、每家机场一次；没席位的用户不分配；旧列表被替换")
    void rebuildsAllActiveUsers() {
        Long a = usableSubscription("A", 300);
        Long b = usableSubscription("B", 300);
        Long c = usableSubscription("C", 300);
        List<Long> users = List.of(activeUser("u1"), activeUser("u2"), activeUser("u3"), activeUser("u4"));
        Long noSeat = fixtures.createUser("no-seat", null);
        fixtures.assignFront(noSeat, a);

        RebuildResult result = runner.applyAll();

        assertThat(result.userCount()).isEqualTo(4);
        assertThat(result.subscriptionCount()).isEqualTo(3);
        for (Long u : users) {
            assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(u)).hasSize(3).containsExactlyInAnyOrder(a, b, c);
        }
        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(noSeat)).isEmpty();
    }

    @Test
    @DisplayName("容量不足：报 410056 带数字，旧列表一行不动")
    void insufficientCapacityAbortsAndKeepsOldLists() {
        Long a = usableSubscription("A", 40);   // 容量 2
        Long u1 = activeUser("u1");
        activeUser("u2");
        activeUser("u3");
        fixtures.assignFront(u1, a);

        assertThatThrownBy(() -> runner.applyAll())
                .isInstanceOf(BizException.class)
                .hasMessageContaining("需要 3 个主用名额，现有 2")
                .extracting(e -> ((BizException) e).getBizCode()).isEqualTo(BizCodeEnum.FRONT_CAPACITY_INSUFFICIENT);
        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(u1)).containsExactly(a);
    }

    @Test
    @DisplayName("没有节点的订阅不算候选、不计容量")
    void emptySubscriptionsAreNotCandidates() {
        Long airportId = fixtures.createAirport("Empty");
        fixtures.createAirportSubscription(airportId, "Empty-01", 3000);
        activeUser("u1");

        assertThatThrownBy(() -> runner.applyAll())
                .hasMessageContaining("需要 1 个主用名额，现有 0");
    }

    @Test
    @DisplayName("preview：按传入设置算需要与现有名额")
    void previewUsesGivenSettings() {
        usableSubscription("A", 300);
        activeUser("u1");
        activeUser("u2");

        assertThat(runner.preview(new ai.mintpop.lane.dto.FrontSettings(ai.mintpop.lane.enumeration.NodeRegion.US, 3, 20)))
                .isEqualTo(new ai.mintpop.lane.response.FrontRebuildPreview(2, 15, true));
        assertThat(runner.preview(new ai.mintpop.lane.dto.FrontSettings(ai.mintpop.lane.enumeration.NodeRegion.US, 3, 200)))
                .isEqualTo(new ai.mintpop.lane.response.FrontRebuildPreview(2, 1, false));
    }

    @Test
    @DisplayName("非主用机场不计主用名额：预检只算主用机场；重算后它只出现在备用位")
    void nonPrimaryAirportOnlyBackup() {
        Long a = usableSubscription("A", 300);
        Long b = usableSubscription("B", 3000);
        fixtures.setAirportPrimaryEnabled(airportSubscriptionRepository.findById(b).orElseThrow().getAirportId(), false);
        List<Long> users = List.of(activeUser("u1"), activeUser("u2"));

        assertThat(runner.preview(new ai.mintpop.lane.dto.FrontSettings(ai.mintpop.lane.enumeration.NodeRegion.US, 3, 20)))
                .isEqualTo(new ai.mintpop.lane.response.FrontRebuildPreview(2, 15, true));

        runner.applyAll();
        for (Long u : users) {
            assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(u)).containsExactly(a, b);
        }
    }
}
