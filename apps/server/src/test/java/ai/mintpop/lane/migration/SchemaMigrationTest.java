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
                INSERT INTO app_user (subject, email, front_node_id, land_node_id)
                VALUES (?, ?, ?, ?)
                """, subject, subject + "@test.example", frontNodeId, landNodeId);
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
    @DisplayName("V8 迁移给 plan 表加出 agent_type 列：非空、带注释、无默认值（新建必须显式传）")
    void v8MigrationAddsPlanAgentType() {
        var column = jdbc.queryForMap("""
                SELECT column_comment, is_nullable, column_default FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'plan' AND column_name = 'agent_type'
                """);
        assertThat((String) column.get("column_comment")).contains("CLAUDE");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
        assertThat(column.get("column_default")).isNull();
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
    @DisplayName("V6 迁移建出 plan 表：套餐名唯一、价格 DECIMAL、列注释落库")
    void v6MigrationCreatesPlanTable() {
        String tableComment = jdbc.queryForObject("""
                SELECT table_comment FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'plan'
                """, String.class);
        assertThat(tableComment).contains("套餐");

        String durationComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'plan' AND column_name = 'duration_days'
                """, String.class);
        assertThat(durationComment).contains("天");

        String priceType = jdbc.queryForObject("""
                SELECT column_type FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'plan' AND column_name = 'price'
                """, String.class);
        assertThat(priceType).isEqualTo("decimal(10,2)");

        Integer uniqueOnName = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'plan'
                  AND column_name = 'name' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnName).isEqualTo(1);
    }

    @Test
    @DisplayName("V7 迁移给 subscription 加分配号与套餐快照列：分配号唯一，快照带注释，不设套餐外键")
    void v7MigrationAddsAssignmentAndPlanSnapshotColumns() {
        // 分配号的列宽在 V12 被收窄，故这里只管「有、且唯一」，宽度交给 V12 的用例断言
        String assignmentComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'assignment_no'
                """, String.class);
        assertThat(assignmentComment).contains("分配");

        Integer uniqueOnAssignment = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name = 'assignment_no' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnAssignment).isEqualTo(1);

        Integer snapshotCols = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name IN ('plan_id', 'plan_duration_days', 'plan_price', 'plan_currency')
                """, Integer.class);
        assertThat(snapshotCols).isEqualTo(4);

        String priceType = jdbc.queryForObject("""
                SELECT column_type FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'plan_price'
                """, String.class);
        assertThat(priceType).isEqualTo("decimal(10,2)");

        String durationComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'plan_duration_days'
                """, String.class);
        assertThat(durationComment).contains("快照");

        // plan_id 是弱引用：subscription 上不许有指向 plan 的外键，否则套餐硬删会被牵制
        Integer fkToPlan = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND referenced_table_name = 'plan'
                """, Integer.class);
        assertThat(fkToPlan).isZero();
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

    @Test
    @DisplayName("V9 迁移删掉 app_user.name，email 升级为唯一键并改了注释")
    void v9MigrationDropsUserNameAndMakesEmailUnique() {
        Integer nameCols = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'name'
                """, Integer.class);
        assertThat(nameCols).isZero();

        Integer uniqueOnEmail = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'app_user'
                  AND column_name = 'email' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnEmail).isEqualTo(1);

        String emailComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'email'
                """, String.class);
        assertThat(emailComment).contains("唯一业务标识");
    }

    @Test
    @DisplayName("V10 迁移建出 enterprise 表：名称与域名各自唯一，agent_types 为 JSON，enabled 默认启用，注释落库")
    void v10MigrationCreatesEnterpriseTable() {
        String tableComment = jdbc.queryForObject("""
                SELECT table_comment FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'enterprise'
                """, String.class);
        assertThat(tableComment).contains("企业");

        String agentTypesType = jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'enterprise' AND column_name = 'agent_types'
                """, String.class);
        assertThat(agentTypesType).isEqualTo("json");

        String domainComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'enterprise' AND column_name = 'domain'
                """, String.class);
        assertThat(domainComment).contains("域名");

        Integer uniqueColumns = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'enterprise'
                  AND column_name IN ('name', 'domain') AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueColumns).isEqualTo(2);

        var enabled = jdbc.queryForMap("""
                SELECT column_default, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'enterprise' AND column_name = 'enabled'
                """);
        assertThat(enabled.get("column_default")).isEqualTo("1");
        assertThat(enabled.get("is_nullable")).isEqualTo("NO");
    }

    @Test
    @DisplayName("V10 迁移给 subscription 加 enterprise_id：可空（NULL 即个人订阅）、带注释、不设外键")
    void v10MigrationAddsSubscriptionEnterpriseId() {
        var column = jdbc.queryForMap("""
                SELECT column_comment, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'enterprise_id'
                """);
        assertThat((String) column.get("column_comment")).contains("个人订阅");
        assertThat(column.get("is_nullable")).isEqualTo("YES");

        Integer foreignKeys = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name = 'enterprise_id' AND referenced_table_name IS NOT NULL
                """, Integer.class);
        assertThat(foreignKeys).isZero();
    }

    @Test
    @DisplayName("V11 迁移给 subscription 加 account_email：可空、带注释、不建唯一索引（允许同一账号重复分配）")
    void v11MigrationAddsSubscriptionAccountEmail() {
        var column = jdbc.queryForMap("""
                SELECT column_comment, is_nullable, column_type FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'account_email'
                """);
        assertThat((String) column.get("column_comment")).contains("账号邮箱");
        assertThat(column.get("is_nullable")).isEqualTo("YES");
        assertThat((String) column.get("column_type")).isEqualTo("varchar(128)");

        Integer uniqueIndexes = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name = 'account_email' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueIndexes).isZero();
    }

    @Test
    @DisplayName("V12 迁移把分配号收窄成 char(10) 短码：注释改口径，唯一键仍在")
    void v12MigrationShortensAssignmentNo() {
        var column = jdbc.queryForMap("""
                SELECT column_type, column_comment, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'assignment_no'
                """);
        assertThat((String) column.get("column_type")).isEqualTo("char(10)");
        assertThat((String) column.get("column_comment")).contains("给用户看");
        assertThat(column.get("is_nullable")).isEqualTo("NO");

        Integer uniqueOnAssignment = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name = 'assignment_no' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnAssignment).isEqualTo(1);
    }
}
