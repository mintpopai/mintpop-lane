package ai.mintpop.lane.repository;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
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

class UserFrontNodeRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;

    @Autowired
    private UserFrontNodeRepository repository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;

    private Long userId;
    private Long nodeA;
    private Long nodeB;
    private Long nodeC;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();

        nodeA = fixtures.createFrontNode("前置A");
        nodeB = fixtures.createFrontNode("前置B");
        nodeC = fixtures.createFrontNode("前置C");
        userId = fixtures.createUser("u1", nodeA, null);
    }

    @Test
    @DisplayName("replaceForUser 整体替换该用户的前置节点集合")
    void replacesWholeSetForUser() {
        repository.replaceForUser(userId, List.of(nodeA, nodeB));
        assertThat(repository.findNodeIdsByUserId(userId)).containsExactlyInAnyOrder(nodeA, nodeB);

        repository.replaceForUser(userId, List.of(nodeB, nodeC));
        assertThat(repository.findNodeIdsByUserId(userId)).containsExactlyInAnyOrder(nodeB, nodeC);
    }

    @Test
    @DisplayName("取回的顺序就是写入顺序（＝分配器的排名），不是 node_id 顺序——"
            + "首选位是客户端真正走的那一跳，被 id 顺序覆盖掉等于把负载摊平的设计作废")
    void findNodeIdsByUserIdKeepsInsertionOrder() {
        // 刻意写成与 node_id 升序不同的顺序：不写 ORDER BY id 时 MySQL 多半按
        // uk_user_front_node(user_id, node_id) 索引返回，也就是 A、B、C
        repository.replaceForUser(userId, List.of(nodeC, nodeA, nodeB));

        assertThat(repository.findNodeIdsByUserId(userId)).containsExactly(nodeC, nodeA, nodeB);
    }

    @Test
    @DisplayName("批量取回同样保住各用户自己的写入顺序")
    void findNodeIdsByUserIdsKeepsInsertionOrder() {
        Long otherUserId = fixtures.createUser("u2", nodeA, null);
        repository.replaceForUser(userId, List.of(nodeC, nodeA));
        repository.replaceForUser(otherUserId, List.of(nodeB, nodeA));

        Map<Long, List<Long>> result = repository.findNodeIdsByUserIds(List.of(userId, otherUserId));

        assertThat(result.get(userId)).containsExactly(nodeC, nodeA);
        assertThat(result.get(otherUserId)).containsExactly(nodeB, nodeA);
    }

    @Test
    @DisplayName("replaceForUser 传空集合等价于清空")
    void replaceWithEmptyClears() {
        repository.replaceForUser(userId, List.of(nodeA));
        repository.replaceForUser(userId, List.of());
        assertThat(repository.findNodeIdsByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("同一用户同一节点不会重复入库")
    void uniqueConstraintPreventsDuplicates() {
        repository.replaceForUser(userId, List.of(nodeA, nodeA));
        assertThat(repository.findNodeIdsByUserId(userId)).containsExactly(nodeA);
    }

    @Test
    @DisplayName("deleteByUserId 只清空该用户的记录，不影响其他用户")
    void deleteByUserIdOnlyAffectsThatUser() {
        Long otherUserId = fixtures.createUser("u2", nodeA, null);
        repository.replaceForUser(userId, List.of(nodeA, nodeB));
        repository.replaceForUser(otherUserId, List.of(nodeA));

        repository.deleteByUserId(userId);

        assertThat(repository.findNodeIdsByUserId(userId)).isEmpty();
        assertThat(repository.findNodeIdsByUserId(otherUserId)).containsExactly(nodeA);
    }

    @Test
    @DisplayName("未分配任何前置节点的用户查询结果是空列表而非报错")
    void findNodeIdsByUserIdReturnsEmptyListWhenUnassigned() {
        assertThat(repository.findNodeIdsByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("findNodeIdsByUserIds 一次批量取回多个用户各自的前置节点集合，互不串味")
    void findNodeIdsByUserIdsGroupsPerUserWithoutCrossContamination() {
        Long otherUserId = fixtures.createUser("u2", nodeA, null);
        repository.replaceForUser(userId, List.of(nodeA, nodeB));
        repository.replaceForUser(otherUserId, List.of(nodeC));

        Map<Long, List<Long>> result = repository.findNodeIdsByUserIds(List.of(userId, otherUserId));

        assertThat(result.get(userId)).containsExactlyInAnyOrder(nodeA, nodeB);
        assertThat(result.get(otherUserId)).containsExactlyInAnyOrder(nodeC);
        // 关键断言：A 的节点不能混进 B 的结果里，反之亦然
        assertThat(result.get(userId)).doesNotContain(nodeC);
        assertThat(result.get(otherUserId)).doesNotContain(nodeA, nodeB);
    }

    @Test
    @DisplayName("findNodeIdsByUserIds 未分配的用户不出现在返回的 Map 里")
    void findNodeIdsByUserIdsOmitsUsersWithoutAnyAssignment() {
        Long otherUserId = fixtures.createUser("u2", nodeA, null);
        repository.replaceForUser(userId, List.of(nodeA));
        // otherUserId 没有调用 replaceForUser，不应出现在结果里

        Map<Long, List<Long>> result = repository.findNodeIdsByUserIds(List.of(userId, otherUserId));

        assertThat(result).containsOnlyKeys(userId);
    }

    @Test
    @DisplayName("findNodeIdsByUserIds 传空集合返回空 Map，不查库、不报错")
    void findNodeIdsByUserIdsWithEmptyCollectionReturnsEmptyMap() {
        assertThat(repository.findNodeIdsByUserIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("countUsersByNodeId 按节点分组统计已分配用户数，未被任何人用的节点不出现在结果里")
    void countUsersByNodeIdGroupsByNode() {
        Long otherUserId = fixtures.createUser("u2", nodeA, null);
        repository.replaceForUser(userId, List.of(nodeA, nodeB));
        repository.replaceForUser(otherUserId, List.of(nodeA));
        // nodeC 没有任何用户绑定，不该出现在统计结果里

        Map<Long, Long> counts = repository.countUsersByNodeId();

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(Map.of(nodeA, 2L, nodeB, 1L));
        assertThat(counts).doesNotContainKey(nodeC);
    }
}
