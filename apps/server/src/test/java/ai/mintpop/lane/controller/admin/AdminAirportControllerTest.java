package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserFrontSubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
@DisplayName("机场管理：增删改查、重名、删除保护")
class AdminAirportControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;
    @Autowired private UserFrontSubscriptionRepository userFrontSubscriptionRepository;
    @Autowired private SessionTokenService sessionTokenService;

    private DatabaseFixtures fixtures;
    private Long adminId;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        // 此时夹具还是「前置节点 + 落地节点」两个参数的旧签名，Task 5 批量改掉
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
    }

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> body(String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("websiteUrl", "https://taishan.example.com");
        body.put("remark", "主力");
        return body;
    }

    @Test
    @DisplayName("新建后列表可见：带订阅数、当前主用人数与主用总容量（全部订阅 带宽/20 之和）")
    void createAndList() throws Exception {
        mockMvc.perform(post("/api/admin/airports").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body("泰山云"))))
                .andExpect(jsonPath("$.code").value(0));
        Long airportId = airportRepository.findAll().get(0).getId();
        Long subId = fixtures.createAirportSubscription(airportId, "ts-01", 300);
        fixtures.createAirportSubscription(airportId, "ts-02", 110);
        Long userId = fixtures.createUser("logto-front-user", null, null);
        userFrontSubscriptionRepository.replaceForUser(userId, List.of(subId));

        mockMvc.perform(get("/api/admin/airports").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("泰山云"))
                .andExpect(jsonPath("$.data[0].websiteUrl").value("https://taishan.example.com"))
                .andExpect(jsonPath("$.data[0].subscriptionCount").value(2))
                .andExpect(jsonPath("$.data[0].primaryUsed").value(1))
                // 300/20=15，110/20=5（向下取整）
                .andExpect(jsonPath("$.data[0].primaryCapacity").value(20));
    }

    @Test
    @DisplayName("重名报 410052；只改大小写的改名不误判为重名")
    void duplicateName() throws Exception {
        Long id = fixtures.createAirport("Taishan");
        fixtures.createAirport("B 机场");
        mockMvc.perform(post("/api/admin/airports").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body("B 机场"))))
                .andExpect(jsonPath("$.code").value(410052));
        mockMvc.perform(put("/api/admin/airports/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body("TAISHAN"))))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(airportRepository.findById(id).orElseThrow().getName()).isEqualTo("TAISHAN");
    }

    @Test
    @DisplayName("机场下还有订阅时删除报 410053；删光订阅后可删；不存在报 410051")
    void deleteProtection() throws Exception {
        Long id = fixtures.createAirport("泰山云");
        Long subId = fixtures.createAirportSubscription(id, "ts-01", 300);

        mockMvc.perform(delete("/api/admin/airports/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410053));

        airportSubscriptionRepository.deleteById(subId);
        mockMvc.perform(delete("/api/admin/airports/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(delete("/api/admin/airports/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410051));
    }
}
