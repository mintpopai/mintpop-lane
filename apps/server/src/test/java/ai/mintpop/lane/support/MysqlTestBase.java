package ai.mintpop.lane.support;

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
// 配置来源钉死 classpath：默认位置含 file:./config/，开发机若放了真实 application.yml
// 会以更高优先级盖过 application-test.yaml，测试值被真实配置污染、甚至触发联网的 OIDC 发现
@SpringBootTest(properties = "spring.config.location=classpath:/")
@ActiveProfiles("test")
public abstract class MysqlTestBase {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        // 与生产 JDBC URL 的时区参数一致：编解码与会话时区都钉 UTC
        MYSQL.withUrlParam("connectionTimeZone", "UTC")
             .withUrlParam("forceConnectionTimeZoneToSession", "true");
        MYSQL.start();
    }
}
