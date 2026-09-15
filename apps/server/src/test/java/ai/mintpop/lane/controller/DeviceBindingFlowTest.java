package ai.mintpop.lane.controller;

import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.DeviceRebindNotifyService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static ai.mintpop.lane.enumeration.AgentType.CLAUDE;
import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「一份订阅只能在一台设备上使用」的整条链路：把六个接口按用户真实会走的顺序串一遍。
 *
 * <p>其余用例各自只盯一个接口的一种情形，证明不了这几步**接起来**是对的：
 * 绑定之后凭据是否真的开始下发、另一台机器看到的是不是「绑在别处」并带得出那台机器的名字、
 * 提了申请之后本机是否真看得到 pendingRequest、管理员同意之后凭据是否真的从旧机器挪到了新机器。
 * 这条测试取代了计划里那段「起服务、按顺序 curl 六个接口」的人工走查——它从没被真正执行过。
 */
@AutoConfigureMockMvc
class DeviceBindingFlowTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    /** 真发飞书卡片不是本用例要验证的东西，替身掉 */
    @MockitoBean
    private DeviceRebindNotifyService deviceRebindNotifyService;

    private static final String DEVICE_A = "a".repeat(64);
    private static final String DEVICE_B = "b".repeat(64);
    private static final String DEVICE_A_NAME = "月白的 MacBook";
    private static final String CREDENTIAL = "sk-ant-席位凭据";

    private Long ownerId;
    private Long adminId;
    private Long subscriptionId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private static String bindBodyJson(String name) {
        return "{\"name\":\"" + name + "\",\"os\":\"macos 26.6.1\",\"model\":\"Mac17,9\"}";
    }

    private static String rebindBodyJson(String name) {
        return "{\"name\":\"" + name + "\",\"os\":\"macos 26.6.1\",\"model\":\"Mac17,9\","
                + "\"reason\":\"换了新电脑\"}";
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures =
                new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long front = fixtures.createFrontNode("FRONT-1");
        Long land = fixtures.createLandNode("LAND-1", "203.0.113.7");
        ownerId = fixtures.createUser("logto-owner", front, land);
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, front, land);
        subscriptionId = fixtures.createSubscription(ownerId, CLAUDE, "Claude 月付",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), CREDENTIAL);
    }

    /** 拉一次链路配置，并对唯一那条席位做断言 */
    private ResultActions config(String deviceId) throws Exception {
        return mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", deviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.agentCredentials.length()").value(1));
    }

    @Test
    @DisplayName("整条链路：未绑定 → 绑到 A → B 看到绑在别处 → B 提申请 → 管理员同意 → 凭据转到 B，A 反过来是绑在别处")
    void wholeDeviceBindingJourney() throws Exception {
        // 1. 还没在任何机器上用过：席位照列（不下发凭据而已），绑定状态如实报 UNBOUND
        config(DEVICE_A)
                .andExpect(jsonPath("$.data.agentCredentials[0].deviceBinding").value("UNBOUND"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value(""))
                .andExpect(jsonPath("$.data.agentCredentials[0].pendingRequest").value(false));

        // 2. 在 A 上确认绑定
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson(DEVICE_A_NAME)))
                .andExpect(jsonPath("$.code").value(0));

        // 3. A 上凭据开始下发——这一步是整个特性的目的
        config(DEVICE_A)
                .andExpect(jsonPath("$.data.agentCredentials[0].deviceBinding").value("BOUND_HERE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value(CREDENTIAL));

        // 4. B 上看到的是「绑在别处」，凭据置空，并带上 A 的主机名好让用户知道它在哪台机器上
        config(DEVICE_B)
                .andExpect(jsonPath("$.data.agentCredentials[0].deviceBinding").value("BOUND_ELSEWHERE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value(""))
                .andExpect(jsonPath("$.data.agentCredentials[0].boundDeviceName").value(DEVICE_A_NAME));

        // 5. 在 B 上提换机申请
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_B)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rebindBodyJson("月白的新 MacBook")))
                .andExpect(jsonPath("$.code").value(0));

        // 6. B 自己看得到「申请已提交、等管理员处理」，凭据仍然不下发
        config(DEVICE_B)
                .andExpect(jsonPath("$.data.agentCredentials[0].deviceBinding").value("BOUND_ELSEWHERE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].pendingRequest").value(true))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value(""));

        // 7. 管理员同意
        Long requestId = jdbc.queryForObject(
                "SELECT id FROM device_rebind_request WHERE subscription_id = ? AND status = 'PENDING'",
                Long.class, subscriptionId);
        mockMvc.perform(post("/api/admin/device-rebind-requests/" + requestId + "/approve")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        // 8. 凭据整份挪到 B，A 反过来变成「绑在别处」——同一时刻只有一台机器能用，闭环
        config(DEVICE_B)
                .andExpect(jsonPath("$.data.agentCredentials[0].deviceBinding").value("BOUND_HERE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value(CREDENTIAL))
                .andExpect(jsonPath("$.data.agentCredentials[0].pendingRequest").value(false));
        config(DEVICE_A)
                .andExpect(jsonPath("$.data.agentCredentials[0].deviceBinding").value("BOUND_ELSEWHERE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value(""))
                .andExpect(jsonPath("$.data.agentCredentials[0].boundDeviceName").value("月白的新 MacBook"));
    }
}
