package ai.mintpop.lane.controller;

import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.REVOKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LinkControllerTest extends MysqlTestBase {

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
    private UserFrontNodeRepository userFrontNodeRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    /** 全用例统一用这台设备的机器码请求头，与下方 setUp 里登记、绑定的那台设备一致 */
    private static final String DEVICE_ID = "a".repeat(64);

    private DatabaseFixtures fixtures;
    private Long user1Id;
    private Long user2Id;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long front = fixtures.createFrontNode("FRONT-1");
        Long land1 = fixtures.createLandNode("LAND-1", "77.47.143.6");
        Long land2 = fixtures.createLandNode("LAND-2", "8.8.8.8");
        user1Id = fixtures.createActiveUser("logto-user-1", front, land1, "sk-ant-test-1");
        user2Id = fixtures.createUser("logto-user-2", MEMBER, REVOKED, front, land2);

        // 把 user1 的席位绑到请求头所用的这台设备上，模拟「已完成绑定」的正常态——
        // 未绑定的订阅不下发凭据，见 LinkServiceImplTest 的绑定关系用例
        UserDevice device = userDeviceRepository.upsert(user1Id, DEVICE_ID, "测试设备", "macos", "MacBook", Instant.now());
        Long subscriptionId = subscriptionRepository.findByUserId(user1Id).getFirst().getId();
        subscriptionRepository.bindDeviceIfUnbound(subscriptionId, device.getId(), Instant.now());
    }

    @Test
    @DisplayName("无令牌访问接口被拒")
    void requestWithoutTokenRejected() throws Exception {
        mockMvc.perform(get("/api/link/config"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("正常用户拿到链路配置，业务码为 0")
    void activeUserGetsLinkConfig() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.front.type").value("trojan"))
                .andExpect(jsonPath("$.data.land.server").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.expectedEgressIp").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value("sk-ant-test-1"))
                .andExpect(jsonPath("$.data.agentCredentials[0].agentType").value("CLAUDE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].assignmentNo",
                        matchesPattern("[0-9A-HJKMNP-TV-Z]{10}")));
    }

    @Test
    @DisplayName("前置组按分配顺序下发，front 就是 front_node_id 指向的那个节点——"
            + "取回若按 node_id 排序，客户端的首选位就会被 id 顺序覆盖掉分配器的负载排名")
    void frontGroupKeepsAllocationOrderAndFrontMatchesPrimary() throws Exception {
        String domain = "relay.order.example.net";
        Long first = fixtures.createFrontNode("ORDER-1", "order-1.example.com", domain);
        Long second = fixtures.createFrontNode("ORDER-2", "order-2.example.com", domain);
        Long third = fixtures.createFrontNode("ORDER-3", "order-3.example.com", domain);
        // 分配器挑出的顺序刻意与 node_id 升序不同：负载最低（＝排第一、写进 front_node_id）的
        // 恰好是 id 最大的那个。三个节点同一个故障域，因此只有一个组
        userFrontNodeRepository.replaceForUser(user1Id, List.of(third, first, second));
        jdbc.update("UPDATE app_user SET front_node_id = ? WHERE id = ?", third, user1Id);

        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.frontGroups").isArray())
                .andExpect(jsonPath("$.data.frontGroups[0].failureDomain").value(domain))
                .andExpect(jsonPath("$.data.frontGroups[0].nodes[0].server").value("order-3.example.com"))
                .andExpect(jsonPath("$.data.frontGroups[0].nodes[1].server").value("order-1.example.com"))
                .andExpect(jsonPath("$.data.frontGroups[0].nodes[2].server").value("order-2.example.com"))
                // front 是老客户端唯一认得的字段，必须与 front_node_id 指向同一个节点
                .andExpect(jsonPath("$.data.front.server").value("order-3.example.com"));
    }

    @Test
    @DisplayName("故障域未解析时 failureDomain 实打实下发成 null，而不是整个字段消失——"
            + "服务端没有全局 JsonInclude(NON_NULL)，客户端 DTO 必须按可空类型声明")
    void nullFailureDomainIsSerializedAsJsonNull() throws Exception {
        // 夹具造的 FRONT-1 没有 failure_domain（手工新建的前置节点永远是这样），
        // 走的正是「关联表为空时退回 front_node_id 单节点」那条路
        String body = mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.frontGroups[0].nodes[0].type").value("trojan"))
                .andReturn().getResponse().getContentAsString();

        // 用原始报文断言：jsonPath 分不清「值是 null」与「字段不存在」，而这两者对客户端
        // 是天壤之别——非可空 DTO 收到 null 会让整份链路配置解析失败
        assertThat(body).contains("\"failureDomain\":null");
    }

    @Test
    @DisplayName("已吊销用户拿不到链路，HTTP 仍为 200 但业务码非 0")
    void revokedUserCannotGetLink() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user2Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(310003))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("不带机器码请求头时被拒，业务码 310007")
    void missingDeviceIdHeaderRejected() throws Exception {
        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(310007))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("心跳返回链路状态")
    void heartbeatReturnsLinkStatus() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("已吊销用户的心跳返回 REVOKED")
    void revokedUserHeartbeatReturnsRevoked() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user2Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    @DisplayName("心跳顺带刷新该设备的最近上报时刻：心跳接口自己并不读机器码，覆盖靠的是安全链上的 DeviceTouchFilter")
    void heartbeatRefreshesDeviceLastSeen() throws Exception {
        Instant stale = Instant.parse("2020-01-01T00:00:00Z");
        userDeviceRepository.upsert(user1Id, DEVICE_ID, "测试设备", "macos", "MacBook", stale);

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(status().isOk());

        assertThat(lastSeenOf(user1Id)).isAfter(stale);
    }

    @Test
    @DisplayName("不带机器码的请求不动任何设备：刷新是按上报的机器码走的，不是见到已认证请求就刷")
    void requestWithoutDeviceHeaderLeavesLastSeenAlone() throws Exception {
        Instant stale = Instant.parse("2020-01-01T00:00:00Z");
        userDeviceRepository.upsert(user1Id, DEVICE_ID, "测试设备", "macos", "MacBook", stale);

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id)))
                .andExpect(status().isOk());

        assertThat(lastSeenOf(user1Id)).isEqualTo(stale);
    }

    private Instant lastSeenOf(Long userId) {
        return userDeviceRepository.findByUserId(userId).getFirst().getLastSeenAt();
    }
}
