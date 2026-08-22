package ai.mintpop.lane.migration;

import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("TRUNCATE TABLE subscription");
        jdbc.execute("TRUNCATE TABLE app_user");
        jdbc.execute("TRUNCATE TABLE proxy_node");
        jdbc.execute("TRUNCATE TABLE node_group");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private long createNode(String name, String role) {
        jdbc.update("""
                INSERT INTO proxy_node (name, role, protocol, server_addr, port)
                VALUES (?, ?, 'SOCKS5', '203.0.113.10', 50101)
                """, name, role);
        return jdbc.queryForObject("SELECT id FROM proxy_node WHERE name = ?", Long.class, name);
    }

    private void createUser(String subject, long frontNodeId, Long landNodeId) {
        jdbc.update("""
                INSERT INTO app_user (subject, email, name, front_node_id, land_node_id)
                VALUES (?, ?, ?, ?, ?)
                """, subject, subject + "@test.example", "测试" + subject, frontNodeId, landNodeId);
    }

    @Test
    @DisplayName("Flyway 迁移建出三张表，且列注释落到了数据库元数据上")
    void migrationCreatesThreeTablesWithComments() {
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN ('proxy_node', 'app_user', 'subscription')
                """, Integer.class);
        assertThat(tables).isEqualTo(3);

        String comment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'land_node_id'
                """, String.class);
        assertThat(comment).contains("NULL 表示尚未分配");
    }

    @Test
    @DisplayName("subscription 表的列注释落到了数据库元数据上")
    void subscriptionTableHasComments() {
        String comment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'ends_at'
                """, String.class);
        assertThat(comment).contains("在期判定");
    }

    @Test
    @DisplayName("多个用户可以同时处于「未分配落地」状态")
    void multipleUsersWithoutLandCanCoexist() {
        long front = createNode("FRONT-1", "FRONT");

        createUser("u1", front, null);
        createUser("u2", front, null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V5 后同一个落地节点可以绑给多个用户（容量制取代一人一座）")
    void sameLandNodeCanBindMultipleUsers() {
        long front = createNode("FRONT-1", "FRONT");
        long land = createNode("LAND-1", "LAND");

        createUser("u1", front, land);
        createUser("u2", front, land);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE land_node_id = ?", Integer.class, land)).isEqualTo(2);
    }

    @Test
    @DisplayName("V5 迁移加出 capacity 列（默认 10、带注释），并把落地唯一索引换成普通索引")
    void v5MigrationAddsCapacityAndDropsLandUniqueIndex() {
        String comment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'proxy_node' AND column_name = 'capacity'
                """, String.class);
        assertThat(comment).contains("容量");

        long land = createNode("LAND-1", "LAND");
        assertThat(jdbc.queryForObject(
                "SELECT capacity FROM proxy_node WHERE id = ?", Integer.class, land)).isEqualTo(10);

        // 一人一座的唯一索引已删除；land_node_id 上保留普通索引供反查与外键使用
        Integer uniqueIndexes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND index_name = 'uk_app_user_land_node'
                """, Integer.class);
        assertThat(uniqueIndexes).isZero();

        Integer plainIndexes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND index_name = 'idx_app_user_land_node' AND non_unique = 1
                """, Integer.class);
        assertThat(plainIndexes).isEqualTo(1);
    }

    @Test
    @DisplayName("V2 迁移建出 node_group 表并带中文注释，proxy_node 挂上分组外键列")
    void v2MigrationCreatesNodeGroupTable() {
        String comment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'node_group' AND column_name = 'sub_url_cipher'
                """, String.class);
        assertThat(comment).contains("AES-GCM");

        Integer cols = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'proxy_node'
                  AND column_name IN ('group_id', 'source_name', 'source_type')
                """, Integer.class);
        assertThat(cols).isEqualTo(3);
    }

    @Test
    @DisplayName("V3 迁移后出口 IP 是单列 egress_ip 并带注释，旧 JSON 列 egress_ips 已删除")
    void v3MigrationReplacesEgressIpsWithSingleColumn() {
        String comment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'proxy_node' AND column_name = 'egress_ip'
                """, String.class);
        assertThat(comment).contains("出口 IP");

        Integer legacyCols = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'proxy_node' AND column_name = 'egress_ips'
                """, Integer.class);
        assertThat(legacyCols).isZero();
    }
}
