package ai.mintpop.lane.support;

import ai.mintpop.lane.service.SubscriptionRenderCache;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
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
 *
 * MYSQL 字段是 protected：数据迁移类测试需要在同一容器里
 * 另建一个独立 schema、绕开 Spring 已经跑到最新版本的默认 schema，用 Flyway Java API 分段跑迁移，
 * 因此要拿到容器的连接坐标（host/port/密码）自己拼 JDBC URL。
 */
// 配置来源钉死 classpath：默认位置含 file:./config/，开发机若放了真实 application.yml
// 会以更高优先级盖过 application-test.yaml，测试值被真实配置污染、甚至触发联网的 OIDC 发现
@SpringBootTest(properties = "spring.config.location=classpath:/")
@ActiveProfiles("test")
// 桌面端接口挂着版本闸门，MockMvc 请求默认带上「最新版」版本头，见 CurrentDesktopClientHeader
@Import(CurrentDesktopClientHeader.class)
public abstract class MysqlTestBase {

    // 钉死到与生产同一小版本：浮动的 mysql:8.4 拉到的是最新小版本，8.4.0 的解析器把 MANUAL 当关键字、
    // 后续小版本又放开了，测试绿而生产 1064（V33 实录）。生产库升级小版本时同步改这里
    @ServiceConnection
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.0");

    @Autowired
    private SubscriptionRenderCache renderCache;

    /**
     * 测试夹具直接写库、绕过服务层，各用例又会复用同样的自增 id；进程内渲染缓存在
     * 上下文缓存下跨用例存活，不清会读到上一个用例留下的节点。
     */
    @BeforeEach
    void evictRenderCache() {
        renderCache.evictAll();
    }

    static {
        // 与生产 JDBC URL 的时区参数一致：编解码与会话时区都钉 UTC
        MYSQL.withUrlParam("connectionTimeZone", "UTC")
             .withUrlParam("forceConnectionTimeZoneToSession", "true");
        MYSQL.start();
    }
}
