package ai.mintpop.lane.controller;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.DeviceRebindCreateRequest;
import ai.mintpop.lane.service.DeviceBindingService;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ai.mintpop.lane.enumeration.AgentType.CLAUDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Autowired private AirportRepository airportRepository;
    @Autowired private AirportSubscriptionRepository airportSubscriptionRepository;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private DeviceBindingService deviceBindingService;

    @Autowired
    private SessionTokenService sessionTokenService;

    /** 通知是否真被触发只由 controller 接线决定，替身掉真正发飞书卡片这一步 */
    @MockitoBean
    private DeviceRebindNotifyService deviceRebindNotifyService;

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

    private static DeviceRebindCreateRequest rebindBody(String tag) {
        DeviceRebindCreateRequest body = new DeviceRebindCreateRequest();
        body.setName("新电脑 " + tag);
        body.setOs("macos 26.6.1");
        body.setModel("Mac17,9");
        body.setReason("换了新电脑 " + tag);
        return body;
    }

    private static String bindBodyJson() {
        return "{\"name\":\"月白的 MacBook\",\"os\":\"macos 26.6.1\",\"model\":\"Mac17,9\"}";
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        ownerId = fixtures.createUser("logto-owner", null);
        strangerId = fixtures.createUser("logto-stranger", null);
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

    @Test
    @DisplayName("过期订阅：绑定与换机申请都被拦下，报 510010")
    void expiredSubscriptionIsRejectedOnBothEndpoints() throws Exception {
        Long expiredId = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository)
                .createSubscription(ownerId, CLAUDE, "Claude 月付（已过期）",
                        Instant.now().minus(60, ChronoUnit.DAYS),
                        Instant.now().minus(1, ChronoUnit.DAYS), "sk-ant-test");

        mockMvc.perform(post("/api/subscriptions/" + expiredId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(510010));
        mockMvc.perform(post("/api/subscriptions/" + expiredId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(510010));

        // 过期订阅一律不绑，额度不该烧在一个根本不下发凭据的席位上
        assertThat(subscriptionRepository.findById(expiredId).orElseThrow().getBoundDeviceId()).isNull();
    }

    @Test
    @DisplayName("待开通订阅（起期未填）：绑定与换机申请都被拦下，报 410041")
    void pendingActivationSubscriptionIsRejectedOnBothEndpoints() throws Exception {
        Long pendingId = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository)
                .createSubscription(ownerId, CLAUDE, "Claude 月付（待开通）", null, null, "sk-ant-test");

        mockMvc.perform(post("/api/subscriptions/" + pendingId + "/device/bind")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(jsonPath("$.code").value(410041));
        mockMvc.perform(post("/api/subscriptions/" + pendingId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(jsonPath("$.code").value(410041));
    }

    @Test
    @DisplayName("两台新机器真并发提换机申请：同一份订阅最终只留下一条 PENDING")
    void concurrentRebindRequestsLeaveExactlyOnePending() throws Exception {
        // 先绑到旧设备，两台新机器才够格提申请
        Instant now = Instant.now();
        UserDevice oldDevice = userDeviceRepository.upsert(
                ownerId, OTHER_DEVICE_ID, "旧电脑", "windows", "", now);
        subscriptionRepository.bindDeviceIfUnbound(subscriptionId, oldDevice.getId(), now);
        String deviceA = "a".repeat(64);
        String deviceB = "b".repeat(64);
        userDeviceRepository.upsert(ownerId, deviceA, "新电脑 A", "macos 26", "", now);
        userDeviceRepository.upsert(ownerId, deviceB, "新电脑 B", "macos 26", "", now);

        // 两个线程各自独立调用注入的 deviceBindingService bean：requestRebind 是 @Transactional，
        // 每个线程因此各开一个事务、各占一条数据库连接，真的是两个事务在抢同一份订阅，
        // 不是同一条连接顺序执行。本测试类没有 @Transactional，不存在测试事务把两者困在一起的问题
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> attemptA = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return deviceBindingService.requestRebind(ownerId, subscriptionId, deviceA, rebindBody("A"));
            });
            Future<Long> attemptB = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return deviceBindingService.requestRebind(ownerId, subscriptionId, deviceB, rebindBody("B"));
            });
            attemptA.get(20, TimeUnit.SECONDS);
            attemptB.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // 两条 PENDING 意味着管理员会看到两条一模一样的待办，同意了过期的那条就把订阅
        // 绑到用户已经放弃的机器上，另一条永远挂着——这正是订阅行锁要挡住的。
        // 实测：把 requestRebind 里的 findByIdForUpdate 注释掉，本用例在 MySQL 8.4 默认的
        // REPEATABLE READ 下**以死锁告终**（两个事务的 supersedePending 各自匹配 0 行、
        // 双双在 idx_device_rebind_subscription 上持有同一段 gap 锁，随后两边的 INSERT
        // 各自等对方的 gap 锁释放），报 DeadlockLoserDataAccessException——用户点一次
        // 「提交申请」直接拿到 110002。只要那条 UPDATE 扫到了真实记录（该订阅本就挂着一条
        // PENDING）或隔离级别是 READ COMMITTED，gap 锁不成立，两条 INSERT 便双双落库，
        // 退化成本用例标题说的两条 PENDING。两种结局都是错的，行锁把它们一起消掉
        Long pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_rebind_request WHERE subscription_id = ? AND status = 'PENDING'",
                Long.class, subscriptionId);
        assertThat(pending).isEqualTo(1L);
        // 先到的那条不是消失，而是被作废——它曾经存在过这件事仍留在库里
        Long superseded = jdbc.queryForObject(
                "SELECT COUNT(*) FROM device_rebind_request WHERE subscription_id = ? AND status = 'SUPERSEDED'",
                Long.class, subscriptionId);
        assertThat(superseded).isEqualTo(1L);
    }

    @Test
    @DisplayName("换机申请成功恰好触发一次通知（带新申请的 id）；失败（未绑在别处）一次都不触发")
    void requestRebindTriggersNotifyExactlyOnceOnSuccessAndNeverOnFailure() throws Exception {
        // 未绑在别处：报 510009，通知一次都不该发——申请压根没落库
        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bindBodyJson()))
                .andExpect(jsonPath("$.code").value(510009));
        verify(deviceRebindNotifyService, never()).notifyRebindRequested(any());

        // 绑到旧设备后，在新设备上提换机申请，应当成功且恰好通知一次
        UserDevice oldDevice = userDeviceRepository.upsert(
                ownerId, OTHER_DEVICE_ID, "旧电脑", "windows", "", Instant.now());
        subscriptionRepository.bindDeviceIfUnbound(subscriptionId, oldDevice.getId(), Instant.now());

        mockMvc.perform(post("/api/subscriptions/" + subscriptionId + "/device/rebind-requests")
                        .header("Authorization", bearer(ownerId))
                        .header("X-Device-Id", DEVICE_ID_MIXED_CASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"月白的 MacBook\",\"os\":\"macos 26.6.1\",\"model\":\"Mac17,9\",\"reason\":\"换了新电脑\"}"))
                .andExpect(jsonPath("$.code").value(0));

        Long newRequestId = jdbc.queryForObject(
                "SELECT id FROM device_rebind_request WHERE subscription_id = ? AND status = 'PENDING'",
                Long.class, subscriptionId);
        verify(deviceRebindNotifyService, times(1)).notifyRebindRequested(newRequestId);
    }
}
