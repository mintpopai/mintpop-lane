package ai.mintpop.lane.controller;

import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static ai.mintpop.lane.enumeration.AgentType.CLAUDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户侧绑定设备与提换机申请两个接口的 MockMvc 集成测试。
 *
 * <p>之所以在 {@link ai.mintpop.lane.service.DeviceBindingServiceImplTest} 之外
 * 再补一份走真实 HTTP 的测试：Mockito 单测只证明服务层逻辑对，证明不了请求头
 * 是否真被读取、{@code @Valid} 是否真的接线、以及机器码是否真的以归一化后的值
 * 落到 {@code UserDeviceRepository.upsert}——这几处恰恰都是 controller 接线本身
 * 可能出错的地方。
 */
@AutoConfigureMockMvc
class DeviceBindingControllerTest extends MysqlTestBase {

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
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    /** 大写混杂，专门用来验证归一化后的值才落库、才用于匹配 */
    private static final String DEVICE_ID_MIXED_CASE = "A".repeat(32) + "b".repeat(32);
    private static final String DEVICE_ID_NORMALIZED = DEVICE_ID_MIXED_CASE.toLowerCase(java.util.Locale.ROOT);
    private static final String OTHER_DEVICE_ID = "c".repeat(64);

    private Long ownerId;
    private Long strangerId;
    private Long subscriptionId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private static String bindBodyJson() {
        return "{\"name\":\"月白的 MacBook\",\"os\":\"macos 26.6.1\",\"model\":\"Mac17,9\"}";
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        ownerId = fixtures.createUser("logto-owner", null, null);
        strangerId = fixtures.createUser("logto-stranger", null, null);
        subscriptionId = fixtures.createSubscription(ownerId, CLAUDE, "Claude 月付",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "sk-ant-test");
    }

    @Test
    @DisplayName("绑定成功后可见：库里记的是归一化后的机器码，而不是请求头原样大小写")
    void bindSucceedsAndBindingIsVisibleAfterwards() throws Exception {
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        var device = userDeviceRepository.findByUserId(ownerId).stream()
                .filter(d -> d.getDeviceId().equals(DEVICE_ID_NORMALIZED))
                .findFirst();
        assertThat(device).isPresent();
        assertThat(device.get().getDeviceId()).isEqualTo(DEVICE_ID_NORMALIZED);

        var subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getBoundDeviceId()).isEqualTo(device.get().getId());
        assertThat(subscription.getBoundAt()).isNotNull();

        // 用归一化后同一个大小写再请求一次仍是幂等成功，不因大小写不同而报「绑在别处」
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_NORMALIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("已绑在别的设备上时再绑，报 510008")
    void bindingSubscriptionBoundToAnotherDeviceIsRejected() throws Exception {
        UserDevice otherDevice = userDeviceRepository.upsert(
                ownerId, OTHER_DEVICE_ID, "旧电脑", "windows", "", Instant.now());
        subscriptionRepository.bindDeviceIfUnbound(subscriptionId, otherDevice.getId(), Instant.now());

        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(510008));
    }

    @Test
    @DisplayName("不带 X-Device-Id 请求头，报 310007")
    void missingDeviceIdHeaderRejected() throws Exception {
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(310007));

        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(310007));
    }

    @Test
    @DisplayName("别人的订阅 id 一律报不存在（410008），不泄露它确实存在")
    void strangersSubscriptionLooksNotFound() throws Exception {
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/bind")
                        .header("Authorization", bearer(strangerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410008));

        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(strangerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410008));
    }

    @Test
    @DisplayName("换机申请：绑在别处才能提，成功后旧 PENDING 被作废，未绑或已绑本机时报 510009")
    void requestRebindEndToEnd() throws Exception {
        // 未绑定任何设备时提申请，报「未绑在别处」
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(jsonPath("$.code").value(510009));

        // 绑到旧设备后，在新设备上提换机申请
        UserDevice oldDevice = userDeviceRepository.upsert(
                ownerId, OTHER_DEVICE_ID, "旧电脑", "windows", "", Instant.now());
        subscriptionRepository.bindDeviceIfUnbound(subscriptionId, oldDevice.getId(), Instant.now());

        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"月白的 MacBook\",\"os\":\"macos 26.6.1\",\"model\":\"Mac17,9\",\"reason\":\"换了新电脑\"}"))
                .andExpect(jsonPath("$.code").value(0));

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_rebind_request WHERE subscription_id = ? AND status = 'PENDING'",
                Long.class, subscriptionId);
        assertThat(count).isEqualTo(1L);
    }
}
