package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import ai.mintpop.lane.util.RebindRequestNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static ai.mintpop.lane.enumeration.AgentType.CLAUDE;
import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端换机申请裁决 + 强制解绑的 MockMvc 集成测试。
 *
 * <p>之所以在 {@link ai.mintpop.lane.service.AdminDeviceServiceImplTest} 之外再补一份走真实
 * HTTP 的测试：Mockito 单测只证明服务层裁决顺序对，证明不了路由是否真的接上、
 * {@code status} 查询参数能否正确转换成 {@link RebindRequestStatus}，以及管理端鉴权
 * （/api/admin/** → ROLE_ADMIN）是否真的把非管理员挡在外面——这几处都是接线本身
 * 可能出错的地方，Mockito 单测覆盖不到。
 */
@AutoConfigureMockMvc
class AdminDeviceControllerTest extends MysqlTestBase {

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
    private DeviceRebindRequestRepository rebindRequestRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    private Long adminId;
    private Long memberId;
    private Long subscriptionId;
    private Long oldDeviceId;
    private Long newDeviceId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    /** 建一条待处理的换机申请：from 指定设备 -> to 指定设备，挂在本用例的订阅上 */
    private Long createPendingRequest(Long fromDeviceId, Long toDeviceId) {
        DeviceRebindRequest r = new DeviceRebindRequest();
        r.setRequestNo(RebindRequestNo.generate(Instant.now()));
        r.setSubscriptionId(subscriptionId);
        r.setUserId(memberId);
        r.setFromDeviceId(fromDeviceId);
        r.setToDeviceId(toDeviceId);
        r.setReason("换了新电脑");
        r.setStatus(RebindRequestStatus.PENDING);
        return rebindRequestRepository.create(r);
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
        memberId = fixtures.createUser("logto-member", MEMBER, ACTIVE, null, null);
        subscriptionId = fixtures.createSubscription(memberId, CLAUDE, "Claude 月付",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), "sk-ant-test");

        Instant now = Instant.now();
        oldDeviceId = userDeviceRepository.upsert(memberId, "device-old", "旧电脑", "macos 26", "", now).getId();
        newDeviceId = userDeviceRepository.upsert(memberId, "device-new", "新电脑", "macos 26", "", now).getId();
        subscriptionRepository.bindDeviceIfUnbound(subscriptionId, oldDeviceId, now);
    }

    @Test
    @DisplayName("同意：订阅改绑到申请的目标设备，申请置为 APPROVED")
    void approveRebindsSubscriptionAndMarksApproved() throws Exception {
        Long requestId = createPendingRequest(oldDeviceId, newDeviceId);

        mockMvc.perform(post("/api/admin/device-rebind-requests/" + requestId + "/approve")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        var subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getBoundDeviceId()).isEqualTo(newDeviceId);

        DeviceRebindRequest request = rebindRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(RebindRequestStatus.APPROVED);
        assertThat(request.getDecidedBy()).isEqualTo(adminId);
        assertThat(request.getDecidedAt()).isNotNull();
    }

    @Test
    @DisplayName("同意一条已被处理过的申请：报 410043，且绑定原封不动")
    void approvingAlreadyDecidedRequestFailsAndLeavesBindingAlone() throws Exception {
        Long requestId = createPendingRequest(oldDeviceId, newDeviceId);
        // 先由另一个管理员把它拒绝掉，模拟「已被处理」——直接调仓储的条件更新，
        // 不走 HTTP：这里只想制造前置状态，不是本用例要验证的行为
        boolean decided = rebindRequestRepository.decide(
                requestId, RebindRequestStatus.REJECTED, adminId, Instant.now());
        assertThat(decided).isTrue();

        mockMvc.perform(post("/api/admin/device-rebind-requests/" + requestId + "/approve")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(410043));

        // 绑定必须还停在建库时绑的旧设备上，同意失败绝不能悄悄把绑定挪走
        var subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getBoundDeviceId()).isEqualTo(oldDeviceId);
    }

    @Test
    @DisplayName("拒绝：只改申请状态为 REJECTED，绑定不受影响")
    void rejectOnlyChangesRequestStatus() throws Exception {
        Long requestId = createPendingRequest(oldDeviceId, newDeviceId);

        mockMvc.perform(post("/api/admin/device-rebind-requests/" + requestId + "/reject")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        DeviceRebindRequest request = rebindRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(RebindRequestStatus.REJECTED);
        assertThat(request.getDecidedBy()).isEqualTo(adminId);

        var subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getBoundDeviceId()).isEqualTo(oldDeviceId);
    }

    @Test
    @DisplayName("强制解绑：清空绑定，并把该订阅挂着的待处理申请一并作废")
    void unbindClearsBindingAndSupersedesPendingRequests() throws Exception {
        Long requestId = createPendingRequest(oldDeviceId, newDeviceId);

        mockMvc.perform(post("/api/admin/subscriptions/" + subscriptionId + "/device/unbind")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        var subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        assertThat(subscription.getBoundDeviceId()).isNull();
        assertThat(subscription.getBoundAt()).isNull();

        DeviceRebindRequest request = rebindRequestRepository.findById(requestId).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(RebindRequestStatus.SUPERSEDED);
    }

    @Test
    @DisplayName("列表按状态过滤，且带上用户邮箱、订阅名与设备信息供管理员判断")
    void listFiltersByStatusAndCarriesDisplayFields() throws Exception {
        Long requestId = createPendingRequest(oldDeviceId, newDeviceId);

        mockMvc.perform(get("/api/admin/device-rebind-requests")
                        .param("status", "PENDING")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(requestId))
                .andExpect(jsonPath("$.data[0].subscriptionName").value("Claude 月付"))
                .andExpect(jsonPath("$.data[0].userEmail").value("logto-member@test.example"))
                .andExpect(jsonPath("$.data[0].fromDevice.name").value("旧电脑"))
                .andExpect(jsonPath("$.data[0].toDevice.name").value("新电脑"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(get("/api/admin/device-rebind-requests")
                        .param("status", "APPROVED")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("非管理员访问四个接口一律 403，安全层直接挡下")
    void nonAdminCallerIsRejectedBySecurityLayer() throws Exception {
        Long requestId = createPendingRequest(oldDeviceId, newDeviceId);

        mockMvc.perform(get("/api/admin/device-rebind-requests")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/device-rebind-requests/" + requestId + "/approve")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/device-rebind-requests/" + requestId + "/reject")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/subscriptions/" + subscriptionId + "/device/unbind")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
    }
}
