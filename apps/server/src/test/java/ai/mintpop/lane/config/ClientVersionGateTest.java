package ai.mintpop.lane.config;

import ai.mintpop.lane.client.LatestClientVersionClient;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.security.ClientVersionInterceptor;
import ai.mintpop.lane.service.ClientVersionService;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import ai.mintpop.lane.util.ClientVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;

import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 版本闸门的挂载范围：桌面端专用接口被拦，控制台也在用的接口不拦；
 * 拦下时是 HTTP 200 + 业务码（客户端据业务码进强制更新），不是 500。
 */
@AutoConfigureMockMvc
class ClientVersionGateTest extends MysqlTestBase {

    private static final int OUTDATED = 310008;
    private static final String OLD = "1.0.0";

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientVersionService clientVersionService;
    @Autowired private SessionTokenService sessionTokenService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;

    @MockitoBean
    private LatestClientVersionClient latestClient;

    private String bearer;

    @BeforeEach
    void setUp() {
        when(latestClient.fetchLatest()).thenReturn(ClientVersion.parse("1.1.0"));
        clientVersionService.refresh();

        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        Long land = fixtures.createLandNode("LAND-1", "77.47.143.6");
        Long userId = fixtures.createActiveUser("logto-user-1", land, "sk-ant-test-1");
        bearer = "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private MockHttpServletRequestBuilder old(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", bearer).header(ClientVersionInterceptor.HEADER, OLD);
    }

    @Test
    @DisplayName("拉链路配置：旧版拿到 CLIENT_VERSION_OUTDATED，HTTP 仍是 200")
    void linkConfigRejectsOldClient() throws Exception {
        mockMvc.perform(old(get("/api/link/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(OUTDATED));
    }

    @Test
    @DisplayName("心跳：旧版被拦，心跳就是在线客户端感知新版的那一路")
    void heartbeatRejectsOldClient() throws Exception {
        mockMvc.perform(old(post("/api/link/heartbeat")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(OUTDATED));
    }

    @Test
    @DisplayName("设备绑定：旧版被拦")
    void deviceBindingRejectsOldClient() throws Exception {
        mockMvc.perform(old(post("/api/subscriptions/1/device/bind")))
                .andExpect(jsonPath("$.code").value(OUTDATED));
    }

    @Test
    @DisplayName("登录兑换：旧版被拦（匿名接口同样过闸门）")
    void desktopExchangeRejectsOldClient() throws Exception {
        mockMvc.perform(post("/api/auth/desktop/exchange").header(ClientVersionInterceptor.HEADER, OLD)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value(OUTDATED));
    }

    @Test
    @DisplayName("版本头形状不对：按旧版拦下")
    void malformedHeaderRejected() throws Exception {
        mockMvc.perform(get("/api/link/config").header("Authorization", bearer)
                        .header(ClientVersionInterceptor.HEADER, "dev"))
                .andExpect(jsonPath("$.code").value(OUTDATED));
    }

    @Test
    @DisplayName("/api/me 控制台也在调：不挂闸门，旧版照常返回")
    void meIsNotGated() throws Exception {
        mockMvc.perform(old(get("/api/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("当前版：闸门放行，请求照常进到接口")
    void currentClientPasses() throws Exception {
        mockMvc.perform(get("/api/link/config").header("Authorization", bearer)
                        .header(ClientVersionInterceptor.HEADER, "1.1.0"))
                .andExpect(jsonPath("$.code").value(not(OUTDATED)));
    }
}
