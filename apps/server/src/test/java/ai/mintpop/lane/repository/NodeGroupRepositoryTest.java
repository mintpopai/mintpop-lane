package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class NodeGroupRepositoryTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NodeGroupRepository groupRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
    }

    private NodeGroupDto newGroup(String name) {
        NodeGroupDto group = new NodeGroupDto();
        group.setName(name);
        group.setSubUrl("https://sub.example.com/c?token=秘密token");
        group.setRemark("测试用");
        return group;
    }

    @Test
    @DisplayName("分组创建后读回明文一致，订阅链接在库里是密文")
    void createReadsBackPlainAndStoresCipher() {
        Long id = groupRepository.create(newGroup("机场A"));

        NodeGroupDto loaded = groupRepository.findById(id).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("机场A");
        assertThat(loaded.getSubUrl()).isEqualTo("https://sub.example.com/c?token=秘密token");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(fixtures.readRawCipherColumn("node_group", "sub_url_cipher", id)).doesNotContain("秘密token");
    }

    @Test
    @DisplayName("existsByName 与改名更新")
    void existsByNameAndRename() {
        Long id = groupRepository.create(newGroup("机场A"));
        assertThat(groupRepository.existsByName("机场A")).isTrue();
        assertThat(groupRepository.existsByName("机场B")).isFalse();

        NodeGroupDto group = groupRepository.findById(id).orElseThrow();
        group.setName("机场B");
        groupRepository.update(group);
        assertThat(groupRepository.findById(id).orElseThrow().getName()).isEqualTo("机场B");
    }

    @Test
    @DisplayName("列表按 id 升序，删除后消失")
    void listAndDelete() {
        Long a = groupRepository.create(newGroup("机场A"));
        groupRepository.create(newGroup("机场B"));
        assertThat(groupRepository.findAll()).hasSize(2);
        assertThat(groupRepository.findAll().get(0).getId()).isEqualTo(a);

        groupRepository.deleteById(a);
        assertThat(groupRepository.findAll()).hasSize(1);
        assertThat(groupRepository.findById(a)).isEmpty();
    }

    @Test
    @DisplayName("按分组与来源名查节点、按分组计数与列表")
    void findNodesByGroup() {
        Long groupId = groupRepository.create(newGroup("机场A"));
        fixtures.createMihomoNode("香港-01", groupId);
        fixtures.createMihomoNode("香港-02", groupId);
        fixtures.createFrontNode("手工节点");

        assertThat(nodeRepository.countByGroupId(groupId)).isEqualTo(2);
        assertThat(nodeRepository.findByGroupId(groupId)).hasSize(2);
        assertThat(nodeRepository.findByGroupIdAndSourceName(groupId, "香港-01")).isPresent();
        assertThat(nodeRepository.findByGroupIdAndSourceName(groupId, "不存在")).isEmpty();
    }
}
