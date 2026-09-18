package ai.mintpop.lane.repository;

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
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
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
