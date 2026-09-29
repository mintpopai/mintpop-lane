package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.repository.*;
import ai.mintpop.lane.response.FrontRebuildStatus;
import ai.mintpop.lane.service.FrontRebuildService;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class AdminSettingControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private SessionTokenService sessionTokenService;
    // 保存设置会触发全体重算；这里只测设置读写，重算由专门的测试覆盖
    @MockitoBean private FrontRebuildService frontRebuildService;
    private Long adminId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null);
        org.mockito.Mockito.when(frontRebuildService.status()).thenReturn(FrontRebuildStatus.idle());
    }

    @Test
    @DisplayName("GET 默认值：US、3、20，地区选项只有美国")
    void getDefaults() throws Exception {
        mockMvc.perform(get("/api/admin/settings").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.region").value("US"))
                .andExpect(jsonPath("$.data.airportsPerUser").value(3))
                .andExpect(jsonPath("$.data.bandwidthPerUserMbps").value(20))
                .andExpect(jsonPath("$.data.regionOptions[0].value").value("US"))
                .andExpect(jsonPath("$.data.regionOptions[0].label").value("美国"));
    }

    @Test
    @DisplayName("PUT 落库并回读；越界报 410054")
    void putPersistsAndValidates() throws Exception {
        mockMvc.perform(put("/api/admin/settings").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("region", "US", "airportsPerUser", 2, "bandwidthPerUserMbps", 25))))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.airportsPerUser").value(2));
        mockMvc.perform(get("/api/admin/settings").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.bandwidthPerUserMbps").value(25));

        mockMvc.perform(put("/api/admin/settings").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("region", "US", "airportsPerUser", 11, "bandwidthPerUserMbps", 20))))
                .andExpect(jsonPath("$.code").value(410054));
    }
}
