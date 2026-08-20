package ai.mintpop.lane.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 真实容器端到端：验证 servlet 容器的 ERROR dispatch（如 {@code response.sendError}）
 * 会保留原生状态码，而不是被安全链拦成 401。
 * MockMvc 是模拟 dispatch，不会真的走一次 ERROR forward，必须起真端口用真实 HTTP 客户端
 * 才能覆盖到这条路径，因此这里用 {@code webEnvironment = RANDOM_PORT}，
 * 并用 Spring Framework 7 的 {@link RestTestClient}（绑定到真实端口）发起请求——
 * Spring Boot 4 已不再提供 TestRestTemplate。
 * <p>
 * 不继承 {@link ai.mintpop.lane.support.MysqlTestBase}：它已经带了一份
 * {@code @SpringBootTest}（默认 MOCK 环境），子类再声明一份
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} 会与父类的定义冲突。
 * 这里选最小可行方案——自带一份 {@code @ServiceConnection} 容器，独立起一份测试上下文。
 */
// spring.config.location 与 MysqlTestBase 同理：钉死 classpath，隔离开发机的 file:./config/
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.location=classpath:/")
@ActiveProfiles("test")
class DesktopLoginControllerIT {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        // 与生产 JDBC URL 的时区参数一致：编解码与会话时区都钉 UTC
        MYSQL.withUrlParam("connectionTimeZone", "UTC")
             .withUrlParam("forceConnectionTimeZoneToSession", "true");
        MYSQL.start();
    }

    @LocalServerPort
    private int port;

    @Test
    void 非法登录参数保留原生400而非被安全链拦成401() {
        RestTestClient client = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        client.get().uri("/auth/desktop/start?code_challenge=bad&state=s")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
