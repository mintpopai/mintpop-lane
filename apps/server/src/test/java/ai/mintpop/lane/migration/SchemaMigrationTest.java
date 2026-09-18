package ai.mintpop.lane.migration;

import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

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

    @Test
    @DisplayName("V13 迁移给 subscription 加五个凭证签发元数据列：全部可空，带注释")
    void v13MigrationAddsSubscriptionCredentialOauthColumns() {
        Integer newCols = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name IN ('credential_scope', 'credential_token_uuid', 'credential_issued_at',
                                      'credential_expires_at', 'credential_refresh_cipher')
                  AND is_nullable = 'YES'
                """, Integer.class);
        assertThat(newCols).isEqualTo(5);

        var scopeColumn = jdbc.queryForMap("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'credential_scope'
                """);
        assertThat((String) scopeColumn.get("column_comment")).contains("旧式凭证");

        var refreshColumn = jdbc.queryForMap("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'credential_refresh_cipher'
                """);
        assertThat((String) refreshColumn.get("column_comment")).contains("refresh_token 密文");
    }

    @Test
    @DisplayName("V13 迁移建出 oauth_session 表：session_id 唯一、subscription_id 有普通索引、表带注释")
    void v13MigrationCreatesOauthSessionTable() {
        String tableComment = jdbc.queryForObject("""
                SELECT table_comment FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'oauth_session'
                """, String.class);
        assertThat(tableComment).contains("授权会话");

        Integer uniqueOnSessionId = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'oauth_session'
                  AND column_name = 'session_id' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnSessionId).isEqualTo(1);

        Integer indexOnSubscriptionId = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'oauth_session'
                  AND index_name = 'idx_oauth_session_subscription'
                """, Integer.class);
        assertThat(indexOnSubscriptionId).isEqualTo(1);

        var codeVerifierColumn = jdbc.queryForMap("""
                SELECT column_comment, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'oauth_session' AND column_name = 'code_verifier_cipher'
                """);
        assertThat((String) codeVerifierColumn.get("column_comment")).contains("PKCE");
        assertThat(codeVerifierColumn.get("is_nullable")).isEqualTo("NO");
    }

    @Test
    @DisplayName("V17 迁移建出 user_device 表：表注释落库，device_id 是非空 CHAR(64) 并带注释，"
            + "(user_id, device_id) 唯一键存在，user_id 外键为 ON DELETE CASCADE")
    void v17MigrationCreatesUserDeviceTable() {
        String tableComment = jdbc.queryForObject("""
                SELECT table_comment FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'user_device'
                """, String.class);
        assertThat(tableComment).contains("设备");

        var deviceIdColumn = jdbc.queryForMap("""
                SELECT column_type, column_comment, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'user_device' AND column_name = 'device_id'
                """);
        assertThat((String) deviceIdColumn.get("column_type")).isEqualTo("char(64)");
        assertThat((String) deviceIdColumn.get("column_comment")).contains("机器码");
        assertThat(deviceIdColumn.get("is_nullable")).isEqualTo("NO");

        Integer uniqueKeyColumns = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT column_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'user_device'
                  AND index_name = 'uk_user_device' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueKeyColumns).isEqualTo(2);

        String deleteRule = jdbc.queryForObject("""
                SELECT delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_user_device_user'
                """, String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    @Test
    @DisplayName("V17 迁移建出 device_rebind_request 表：表注释落库，request_no 唯一，status 带注释，"
            + "from_device_id 可空而 to_device_id 非空（此前未绑定 vs 本次申请改绑到的设备），"
            + "user_id 外键为 ON DELETE CASCADE")
    void v17MigrationCreatesDeviceRebindRequestTable() {
        String tableComment = jdbc.queryForObject("""
                SELECT table_comment FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'device_rebind_request'
                """, String.class);
        assertThat(tableComment).contains("换机申请");

        Integer uniqueOnRequestNo = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'device_rebind_request'
                  AND column_name = 'request_no' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnRequestNo).isEqualTo(1);

        String statusComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'device_rebind_request' AND column_name = 'status'
                """, String.class);
        assertThat(statusComment).contains("PENDING");

        String fromDeviceNullable = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'device_rebind_request'
                  AND column_name = 'from_device_id'
                """, String.class);
        assertThat(fromDeviceNullable).isEqualTo("YES");

        String toDeviceNullable = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'device_rebind_request'
                  AND column_name = 'to_device_id'
                """, String.class);
        assertThat(toDeviceNullable).isEqualTo("NO");

        // 与 fk_user_device_user 一样级联：不级联的话，凡提过换机申请的用户都删不掉——
        // 管理端删用户会撞上这个外键直接失败
        String deleteRule = jdbc.queryForObject("""
                SELECT delete_rule FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_device_rebind_user'
                """, String.class);
        assertThat(deleteRule).isEqualTo("CASCADE");
    }

    @Test
    @DisplayName("V17 迁移给 subscription 加 bound_device_id/bound_at：均可空、带中文注释、不设外键（弱引用，"
            + "解绑与删设备都允许悬空）")
    void v17MigrationAddsSubscriptionBoundDeviceColumns() {
        var boundDeviceIdColumn = jdbc.queryForMap("""
                SELECT column_comment, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'bound_device_id'
                """);
        assertThat((String) boundDeviceIdColumn.get("column_comment")).contains("未绑定");
        assertThat(boundDeviceIdColumn.get("is_nullable")).isEqualTo("YES");

        var boundAtColumn = jdbc.queryForMap("""
                SELECT column_comment, is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'subscription' AND column_name = 'bound_at'
                """);
        assertThat((String) boundAtColumn.get("column_comment")).contains("绑定时刻");
        assertThat(boundAtColumn.get("is_nullable")).isEqualTo("YES");

        Integer foreignKeys = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'subscription'
                  AND column_name IN ('bound_device_id', 'bound_at') AND referenced_table_name IS NOT NULL
                """, Integer.class);
        assertThat(foreignKeys).isZero();
    }

    @Test
    @DisplayName("V20 建出故障域列、订阅额度列与入口 IP 历史表")
    void v20AddsFrontStabilitySchema() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(columnExists(conn, "proxy_node", "failure_domain")).isTrue();
            assertThat(columnExists(conn, "proxy_node", "failure_domain_checked_at")).isTrue();
            assertThat(columnExists(conn, "node_group", "traffic_used_bytes")).isTrue();
            assertThat(columnExists(conn, "node_group", "traffic_total_bytes")).isTrue();
            assertThat(columnExists(conn, "node_group", "traffic_expires_at")).isTrue();
            assertThat(columnExists(conn, "node_group", "traffic_alerted_pct")).isTrue();
            assertThat(columnExists(conn, "node_group", "fetched_at")).isTrue();
            assertThat(tableExists(conn, "entry_ip_history")).isTrue();
        }
    }

    @Test
    @DisplayName("V21 建出 user_front_node 表，并把现有 front_node_id 回填进去")
    void v21AddsUserFrontNodeTable() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(tableExists(conn, "user_front_node")).isTrue();
            assertThat(columnExists(conn, "user_front_node", "user_id")).isTrue();
            assertThat(columnExists(conn, "user_front_node", "node_id")).isTrue();
            assertThat(columnComment(conn, "user_front_node", "node_id")).isNotBlank();
            assertThat(columnComment(conn, "user_front_node", "user_id")).isNotBlank();
            assertThat(columnComment(conn, "user_front_node", "id")).isNotBlank();
            assertThat(columnComment(conn, "user_front_node", "created_at")).isNotBlank();
            assertThat(isNullable(conn, "user_front_node", "user_id")).isFalse();
            assertThat(isNullable(conn, "user_front_node", "node_id")).isFalse();
        }

        // app_user.front_node_id 保留不动，但注释应已收窄为「主前置节点」语义
        String frontNodeComment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'front_node_id'
                """, String.class);
        assertThat(frontNodeComment).contains("主前置节点");

        // user_front_node 与 proxy_node/app_user 之间的外键存在，且 (user_id, node_id) 唯一，
        // 这两点直接决定「回填不会产出脏数据、重复回填不会插出重复行」，比迁移时机上不可控的行数计数更有区分力
        Integer fkToUser = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'user_front_node'
                  AND column_name = 'user_id' AND referenced_table_name = 'app_user'
                """, Integer.class);
        assertThat(fkToUser).isEqualTo(1);

        Integer fkToNode = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE() AND table_name = 'user_front_node'
                  AND column_name = 'node_id' AND referenced_table_name = 'proxy_node'
                """, Integer.class);
        assertThat(fkToNode).isEqualTo(1);

        Integer uniqueOnPair = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT column_name) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'user_front_node'
                  AND index_name = 'uk_user_front_node' AND non_unique = 0
                """, Integer.class);
        assertThat(uniqueOnPair).isEqualTo(2);
    }

    private String columnComment(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT column_comment FROM information_schema.columns "
              + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            ps.setString(1, conn.getCatalog());
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private boolean isNullable(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT is_nullable FROM information_schema.columns "
              + "WHERE table_schema = ? AND table_name = ? AND column_name = ?")) {
            ps.setString(1, conn.getCatalog());
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return "YES".equals(rs.getString(1));
            }
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private boolean tableExists(Connection conn, String table) throws Exception {
        try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, table, null)) {
            return rs.next();
        }
    }
}
