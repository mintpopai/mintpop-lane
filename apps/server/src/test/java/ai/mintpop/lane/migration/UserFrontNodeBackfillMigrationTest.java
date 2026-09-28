package ai.mintpop.lane.migration;

import ai.mintpop.lane.support.MysqlTestBase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据迁移正确性测试：验证 V21 的回填语句「把数据搬对了」，而不只是「表结构建对了」。
 *
 * {@code SchemaMigrationTest} 那类用例只能断言表/列/约束存在——它们在
 * {@code INSERT INTO user_front_node (user_id, node_id) SELECT id, front_node_id FROM ...}
 * 被误写成 {@code SELECT front_node_id, id}（两列都是 BIGINT，外键照样满足）时仍然全绿，
 * 而这条迁移只在生产升级时跑一次，错了没有第二次机会。
 *
 * 做法：复用 {@link MysqlTestBase} 共享的单例容器，但不复用它已经跑到最新版本的默认 schema——
 * 而是在同一容器里新建一个独立、干净的 schema，用 Flyway 的 Java API 分两段跑：
 * 1) 先迁到 V20（回填逻辑引入之前的最后一版），此时 app_user/proxy_node 已建表但都是空的；
 * 2) 手工插入一条「历史数据」（模拟升级前就存在的用户-前置节点绑定）；
 * 3) 再迁到最新版本，触发 V21 的回填；
 * 4) 断言 user_front_node 里的 user_id/node_id 分别等于历史用户、历史节点的 id ——
 *    必须分别断言两列的值，只断言「有一行」拦不住列写反。
 *
 * 这是本仓第一条覆盖「数据迁移正确性」（而非单纯表结构）的用例。后续新增涉及数据回填/
 * 搬迁的迁移，照此结构复制一份、改 schema 名与断言的表/列即可。
 */
class UserFrontNodeBackfillMigrationTest extends MysqlTestBase {

    @Test
    @DisplayName("V21 的回填把 app_user.front_node_id 正确地搬进 user_front_node：user_id/node_id 没有写反")
    void v21BackfillsFrontNodeIdWithCorrectColumnMapping() throws Exception {
        String schema = "backfill_check_" + System.nanoTime();
        // testcontainers 的 MySQLContainer：username 不是 root 时，MYSQL_ROOT_PASSWORD 与
        // MYSQL_PASSWORD 被设成同一个值，因此 root 密码就是 MYSQL.getPassword()
        String rootPassword = MYSQL.getPassword();
        String host = MYSQL.getHost();
        int port = MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT);
        String adminUrl = "jdbc:mysql://" + host + ":" + port + "/?useSSL=false&allowPublicKeyRetrieval=true";
        String schemaUrl = "jdbc:mysql://" + host + ":" + port + "/" + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true";

        try (Connection admin = DriverManager.getConnection(adminUrl, "root", rootPassword);
             Statement stmt = admin.createStatement()) {
            stmt.execute("CREATE DATABASE `" + schema + "`");
        }

        try {
            // 第一段：只迁到 V20（回填逻辑引入之前）
            Flyway.configure()
                    .dataSource(schemaUrl, "root", rootPassword)
                    .locations("classpath:db/migration")
                    .target("20")
                    .load()
                    .migrate();

            long historyNodeId;
            long historyUserId;
            try (Connection conn = DriverManager.getConnection(schemaUrl, "root", rootPassword)) {
                // 造数据的关键设计：不能只让「历史节点 id」≠「历史用户 id」，
                // 还必须让「反过来那个 id」在对方表里也存在，两张表都补足够的干扰行——
                // 否则列写反后 FK 会直接报错，测试是被 FK「意外」拦住的，测不出「列写反但 FK 照样通过、
                // 静默插出错误数据」这个真正要防的失败形态（评审原话）。
                // 下面让 proxy_node 与 app_user 的合法 id 都覆盖 {1,2,3}，历史节点=3、历史用户=1，
                // 这样列写反后 user_id=3（app_user 里存在）、node_id=1（proxy_node 里存在），
                // FK 两边都能通过，唯一能拦住它的只剩「断言用户 id/节点 id 的具体值」。
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("""
                            INSERT INTO proxy_node (name, role, protocol, server_addr, port)
                            VALUES ('干扰节点1', 'LAND', 'SOCKS5', '203.0.113.30', 50101),
                                   ('干扰节点2', 'LAND', 'SOCKS5', '203.0.113.32', 50101),
                                   ('历史前置节点', 'FRONT', 'SOCKS5', '203.0.113.31', 50101)
                            """);
                }
                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT id FROM proxy_node WHERE name = '历史前置节点'")) {
                    assertThat(rs.next()).isTrue();
                    historyNodeId = rs.getLong(1);
                }

                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("""
                            INSERT INTO app_user (subject, email, front_node_id)
                            VALUES ('历史用户', 'legacy@test.example', %d)
                            """.formatted(historyNodeId));
                    // 补两个干扰用户，让 app_user 的合法 id 也覆盖到历史节点 id（3）那个值
                    stmt.executeUpdate("""
                            INSERT INTO app_user (subject, email)
                            VALUES ('干扰用户1', 'decoy1@test.example'),
                                   ('干扰用户2', 'decoy2@test.example')
                            """);
                }
                try (ResultSet rs = conn.createStatement()
                        .executeQuery("SELECT id FROM app_user WHERE subject = '历史用户'")) {
                    assertThat(rs.next()).isTrue();
                    historyUserId = rs.getLong(1);
                }
            }

            // 干扰行必须真的起到了作用：两个 id 不相等，且互为对方表里的合法 id，
            // 否则上面这套「让列写反也能绕过 FK」的设计本身就失效了
            assertThat(historyUserId).isNotEqualTo(historyNodeId);

            // 第二段：只迁到 V21，触发回填。不能跑到最新版本——V26（机场订阅按机场分配）
            // 会清空 user_front_node、proxy_node 里的 FRONT 节点与 app_user.front_node_id
            // （spec §3.1：不做旧数据迁移），跑到最新版本会把这里刚回填出的那一行冲掉，
            // 而这条用例只关心「V21 的回填语句本身有没有把列写对」，与之后版本的清空逻辑无关
            Flyway.configure()
                    .dataSource(schemaUrl, "root", rootPassword)
                    .locations("classpath:db/migration")
                    .target("21")
                    .load()
                    .migrate();

            try (Connection conn = DriverManager.getConnection(schemaUrl, "root", rootPassword);
                 ResultSet rs = conn.createStatement()
                         .executeQuery("SELECT user_id, node_id FROM user_front_node")) {
                assertThat(rs.next()).as("回填后 user_front_node 应恰好有一行").isTrue();
                assertThat(rs.getLong("user_id"))
                        .as("user_id 必须是历史用户的 id，不能被列写反成节点 id")
                        .isEqualTo(historyUserId);
                assertThat(rs.getLong("node_id"))
                        .as("node_id 必须是历史节点的 id，不能被列写反成用户 id")
                        .isEqualTo(historyNodeId);
                assertThat(rs.next()).as("不应回填出多余的行").isFalse();
            }
        } finally {
            try (Connection admin = DriverManager.getConnection(adminUrl, "root", rootPassword);
                 Statement stmt = admin.createStatement()) {
                stmt.execute("DROP DATABASE IF EXISTS `" + schema + "`");
            }
        }
    }
}
