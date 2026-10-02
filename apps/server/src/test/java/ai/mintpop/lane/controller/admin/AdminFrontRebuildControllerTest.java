package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.repository.*;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.service.FrontRebuildService;
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
import java.time.Instant;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class AdminFrontRebuildControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private SessionTokenService sessionTokenService;
    // 只测接线：真正的重算逻辑由 FrontRebuildRunnerTest / FrontRebuildServiceImplTest 覆盖
    @MockitoBean private FrontRebuildService frontRebuildService;
    private DatabaseFixtures fixtures;
    private Long adminId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null);
        org.mockito.Mockito.when(frontRebuildService.status()).thenReturn(ai.mintpop.lane.response.FrontRebuildStatus.idle());
        org.mockito.Mockito.when(frontRebuildService.preview(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ai.mintpop.lane.response.FrontRebuildPreview(2, 15, true));
    }

    @Test
    @DisplayName("POST 启动；GET 读状态；GET preview 透传设置")
    void startStatusPreview() throws Exception {
        mockMvc.perform(post("/api/admin/front/rebuild").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        verify(frontRebuildService).start();

        mockMvc.perform(get("/api/admin/front/rebuild").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.phase").value("IDLE"));

        mockMvc.perform(get("/api/admin/front/rebuild/preview").header("Authorization", bearer(adminId))
                        .param("region", "US").param("bandwidthPerUserMbps", "20"))
                .andExpect(jsonPath("$.data.requiredPrimary").value(2))
                .andExpect(jsonPath("$.data.availablePrimary").value(15))
                .andExpect(jsonPath("$.data.sufficient").value(true));
    }

    @Test
    @DisplayName("preview 带宽越界（0）报 410054，不进入服务层")
    void previewRejectsOutOfRangeBandwidth() throws Exception {
        mockMvc.perform(get("/api/admin/front/rebuild/preview").header("Authorization", bearer(adminId))
                        .param("region", "US").param("bandwidthPerUserMbps", "0"))
                .andExpect(jsonPath("$.code").value(410054));
    }

    @Test
    @DisplayName("保存设置：值变没变都不触发 start（整体重分配只走手动按钮）；正在跑时 PUT 报 410055 且不保存")
    void settingsUpdateNeverTriggersRebuild() throws Exception {
        mockMvc.perform(put("/api/admin/settings").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("region", "US", "airportsPerUser", 3, "bandwidthPerUserMbps", 20))))
                .andExpect(jsonPath("$.code").value(0));
        verify(frontRebuildService, never()).start();

        mockMvc.perform(put("/api/admin/settings").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("region", "US", "airportsPerUser", 2, "bandwidthPerUserMbps", 20))))
                .andExpect(jsonPath("$.code").value(0));
        verify(frontRebuildService, never()).start();

        org.mockito.Mockito.when(frontRebuildService.status()).thenReturn(new ai.mintpop.lane.response.FrontRebuildStatus(
                ai.mintpop.lane.enumeration.FrontRebuildPhase.RUNNING, Instant.now(), null, null, null, null));
        mockMvc.perform(put("/api/admin/settings").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("region", "US", "airportsPerUser", 4, "bandwidthPerUserMbps", 20))))
                .andExpect(jsonPath("$.code").value(410055));
        mockMvc.perform(get("/api/admin/settings").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.airportsPerUser").value(2));
    }
}
