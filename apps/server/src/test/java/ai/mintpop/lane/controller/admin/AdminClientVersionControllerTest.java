package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.client.LatestClientVersionClient;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import ai.mintpop.lane.util.ClientVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminClientVersionControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private SessionTokenService sessionTokenService;

    @MockitoBean private LatestClientVersionClient latestClient;

    private String adminBearer;
    private String memberBearer;

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        adminBearer = "Bearer " + sessionTokenService.issue(
                fixtures.createUser("logto-admin", ADMIN, ACTIVE, null), Duration.ofMinutes(10));
        memberBearer = "Bearer " + sessionTokenService.issue(
                fixtures.createUser("logto-member", MEMBER, ACTIVE, null), Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("手动拉取成功：回最新版本、拉取时刻与清单地址，GET 读到同一份")
    void refreshThenGet() throws Exception {
        when(latestClient.fetchLatest()).thenReturn(ClientVersion.parse("1.2.0"));

        mockMvc.perform(post("/api/admin/client-version/refresh").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.latest").value("1.2.0"))
                .andExpect(jsonPath("$.data.fetchedAt").value(notNullValue()))
                .andExpect(jsonPath("$.data.lastAttemptFailed").value(false))
                .andExpect(jsonPath("$.data.manifestUrl").value("http://127.0.0.1:9/latest.json"));

        mockMvc.perform(get("/api/admin/client-version").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.data.latest").value("1.2.0"));
    }

    @Test
    @DisplayName("手动拉取失败：不报错，沿用上一次的版本并标记失败")
    void refreshFailureKeepsPrevious() throws Exception {
        when(latestClient.fetchLatest()).thenReturn(ClientVersion.parse("1.2.0"));
        mockMvc.perform(post("/api/admin/client-version/refresh").header("Authorization", adminBearer));

        when(latestClient.fetchLatest()).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/admin/client-version/refresh").header("Authorization", adminBearer))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.latest").value("1.2.0"))
                .andExpect(jsonPath("$.data.lastAttemptFailed").value(true));
    }

    @Test
    @DisplayName("普通用户无权查看或触发")
    void memberForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/client-version").header("Authorization", memberBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/client-version/refresh").header("Authorization", memberBearer))
                .andExpect(status().isForbidden());
    }
}
