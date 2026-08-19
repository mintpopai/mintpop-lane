package ai.mintpop.pier.migration;

import ai.mintpop.pier.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaMigrationTest extends MysqlTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void 清空数据() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("TRUNCATE TABLE app_user");
        jdbc.execute("TRUNCATE TABLE proxy_node");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    private long 建节点(String name, String role) {
        jdbc.update("""
                INSERT INTO proxy_node (name, role, protocol, server_addr, port)
                VALUES (?, ?, 'SOCKS5', '203.0.113.10', 50101)
                """, name, role);
        return jdbc.queryForObject("SELECT id FROM proxy_node WHERE name = ?", Long.class, name);
    }

    private void 建用户(String subject, long frontNodeId, Long landNodeId) {
        jdbc.update("""
                INSERT INTO app_user (subject, name, front_node_id, land_node_id)
                VALUES (?, ?, ?, ?)
                """, subject, "测试" + subject, frontNodeId, landNodeId);
    }

    @Test
    @DisplayName("Flyway 迁移建出两张表，且列注释落到了数据库元数据上")
    void 迁移建出两张表并带中文注释() {
        Integer tables = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN ('proxy_node', 'app_user')
                """, Integer.class);
        assertThat(tables).isEqualTo(2);

        String comment = jdbc.queryForObject("""
                SELECT column_comment FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'app_user' AND column_name = 'land_node_id'
                """, String.class);
        assertThat(comment).contains("NULL 表示尚未分配");
    }

    @Test
    @DisplayName("多个用户可以同时处于「未分配落地」状态")
    void 多个未分配落地的用户可以共存() {
        long front = 建节点("FRONT-1", "FRONT");

        建用户("u1", front, null);
        建用户("u2", front, null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("同一个落地节点绑给第二个人时被数据库拒绝")
    void 同一落地节点不能绑两个人() {
        long front = 建节点("FRONT-1", "FRONT");
        long land = 建节点("LAND-1", "LAND");

        建用户("u1", front, land);

        assertThatThrownBy(() -> 建用户("u2", front, land))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
