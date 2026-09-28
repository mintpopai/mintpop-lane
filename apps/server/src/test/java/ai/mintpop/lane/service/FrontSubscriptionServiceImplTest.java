package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.FrontSubscriptionBrief;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("第一跳订阅分配服务：落库、加锁、边界")
class FrontSubscriptionServiceImplTest extends MysqlTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    @Autowired private FrontSubscriptionService frontSubscriptionService;

    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
    }

    /** 建一个机场 + 一个带一个启用美国节点的订阅，返回订阅 id */
    private Long usableSubscription(String airport, int bandwidth) {
        Long airportId = fixtures.createAirport(airport);
        Long subId = fixtures.createAirportSubscription(airportId, airport + "-01", bandwidth);
        fixtures.createSubscriptionNode(subId, "🇺🇸[US]" + airport + "-01", NodeStatus.ENABLED);
        return subId;
    }

    @Test
    @DisplayName("分配结果按顺位落库，摘要带机场名、订阅名、账号")
    void allocatesAndPersistsInOrder() {
        Long a = usableSubscription("A", 300);
        Long b = usableSubscription("B", 300);
        Long user = fixtures.createUser("u1", null);

        List<FrontSubscriptionBrief> briefs = frontSubscriptionService.allocate(user);

        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(user)).containsExactly(a, b);
        assertThat(briefs).extracting(FrontSubscriptionBrief::airportName).containsExactly("A", "B");
        assertThat(briefs.get(0).account()).isEqualTo("A-01@airport.example");
    }

    @Test
    @DisplayName("重算已分配用户时排除他自己的旧列表：满额前最后一个名额能分回给他")
    void allocatesExcludingUsersOwnOldList() {
        Long a = usableSubscription("A", 20);          // 容量 1
        Long user = fixtures.createUser("u1", null);
        frontSubscriptionService.allocate(user);

        // 第二次重算：若把他自己算进负载，A 会被判满额而报错
        assertThat(frontSubscriptionService.allocate(user))
                .extracting(FrontSubscriptionBrief::airportSubscriptionId).containsExactly(a);
    }

    @Test
    @DisplayName("主用名额全满时报 410050，原列表不动")
    void capacityFullKeepsOldList() {
        usableSubscription("A", 20);                   // 容量 1
        Long first = fixtures.createUser("u1", null);
        Long second = fixtures.createUser("u2", null);
        frontSubscriptionService.allocate(first);

        assertThatThrownBy(() -> frontSubscriptionService.allocate(second))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.getBizCode().getCode()).isEqualTo(410050));
        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(second)).isEmpty();
    }

    @Test
    @DisplayName("没有可用美国节点的订阅不参与分配：节点全禁用、或只有非美国节点")
    void skipsSubscriptionsWithoutUsableNodes() {
        Long airportA = fixtures.createAirport("A");
        Long disabledOnly = fixtures.createAirportSubscription(airportA, "A-01", 300);
        fixtures.createSubscriptionNode(disabledOnly, "🇺🇸[US]A-01", NodeStatus.DISABLED);
        Long airportB = fixtures.createAirport("B");
        Long hkOnly = fixtures.createAirportSubscription(airportB, "B-01", 300);
        fixtures.createSubscriptionNode(hkOnly, "🇭🇰[HK]B-01", NodeStatus.ENABLED);
        Long usable = usableSubscription("C", 300);
        Long user = fixtures.createUser("u1", null);

        frontSubscriptionService.allocate(user);

        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(user)).containsExactly(usable);
    }

    @Test
    @DisplayName("取消分配清空列表，释放主用名额")
    void clearReleasesPrimary() {
        usableSubscription("A", 20);
        Long first = fixtures.createUser("u1", null);
        Long second = fixtures.createUser("u2", null);
        frontSubscriptionService.allocate(first);

        frontSubscriptionService.clear(first);

        assertThat(userFrontSubscriptionRepository.findSubscriptionIdsByUserId(first)).isEmpty();
        assertThat(frontSubscriptionService.allocate(second)).hasSize(1);
    }

    @Test
    @DisplayName("并发分配时同一主用订阅不超容量")
    void concurrentAllocationsNeverOverfillPrimary() throws Exception {
        Long a = usableSubscription("A", 60);          // 容量 3
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            users.add(fixtures.createUser("u" + i, null));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Long user : users) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    frontSubscriptionService.allocate(user);
                } catch (BizException ignored) {
                    // 名额满的那几个报 410050 是预期
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(userFrontSubscriptionRepository.countPrimaryByAirportSubscription().get(a)).isEqualTo(3L);
    }
}
