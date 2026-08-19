package com.mintpop.server.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 需要数据库的测试的公共基类。
 *
 * 容器是「单例」的：static 字段 + 静态块启动，整个测试 JVM 只起一个 MySQL，
 * 各测试类共用（Spring 的测试上下文缓存也因此只建一次）。容器不显式 stop，
 * 由 Testcontainers 的 Ryuk 在 JVM 退出后回收。
 *
 * @ServiceConnection 让 Spring Boot 自动把容器的 JDBC 连接信息注入 DataSource，
 * 因此测试配置里不需要写 spring.datasource.*。
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class MysqlTestBase {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start();
    }
}
