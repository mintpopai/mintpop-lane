package ai.mintpop.lane.controller;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.IpAsnClient.AsnInfo;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.NodeStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.REVOKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
    private SessionTokenService sessionTokenService;

    @MockitoBean
    private IpAsnClient ipAsnClient;

    @MockitoBean
    private Clock clock;

    /** 全用例统一用这台设备的机器码请求头，与下方 setUp 里登记、绑定的那台设备一致 */
    private static final String DEVICE_ID = "a".repeat(64);

    /**
     * 测试用的固定「现在」：本类里所有上报块的 windowStart 字面量都是
     * {@code 2026-09-19T00:00:00Z}，这里取它 3 分钟之后，让那些字面量安全落在
     * 窗口容忍范围（默认过去 1 小时/未来 5 分钟）内，不受真实系统时钟影响
     */
    private static final Instant FIXED_NOW = Instant.parse("2026-09-19T00:03:00Z");

    private DatabaseFixtures fixtures;
    private Long user1Id;
    private Long user2Id;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        // 默认让 clock 表现得跟真实时钟一样：GET /config 等与心跳窗口无关的用例依赖
        // fixture 建的订阅有效期是按「真实现在」算的（见 DatabaseFixtures），这里必须贴近
        // 真实时间。心跳窗口边界相关的用例会在各自方法里用 when(...) 覆盖这个默认桩，
        // 改用与其 windowStart 字面量对齐的固定时间，不依赖当前系统时间是否恰好落在
        // 容忍范围内（那样测试会随沙箱系统时间漂移而变得不稳定）
        when(clock.instant()).thenReturn(Instant.now());

        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        Long land1 = fixtures.createLandNode("LAND-1", "77.47.143.6");
        Long land2 = fixtures.createLandNode("LAND-2", "8.8.8.8");
        user1Id = fixtures.createActiveUser("logto-user-1", land1, "sk-ant-test-1");
        user2Id = fixtures.createUser("logto-user-2", MEMBER, REVOKED, land2);

        // 第一跳只能来自机场订阅：给 user1 分配一个默认订阅，下面有一个可用的美国节点
        Long airportId = fixtures.createAirport("测试机场");
        Long subscriptionGroupId = fixtures.createAirportSubscription(airportId, "front-sub-1", 300);
        fixtures.createSubscriptionNode(subscriptionGroupId, "🇺🇸[US]Front-01", NodeStatus.ENABLED);
        fixtures.assignFront(user1Id, subscriptionGroupId);

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
                .andExpect(jsonPath("$.data.front").doesNotExist())
                .andExpect(jsonPath("$.data.frontGroups[0].nodes[0].type").value("anytls"))
                .andExpect(jsonPath("$.data.land.server").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.expectedEgressIp").value("77.47.143.6"))
                .andExpect(jsonPath("$.data.agentCredentials[0].credential").value("sk-ant-test-1"))
                .andExpect(jsonPath("$.data.agentCredentials[0].agentType").value("CLAUDE"))
                .andExpect(jsonPath("$.data.agentCredentials[0].assignmentNo",
                        matchesPattern("[0-9A-HJKMNP-TV-Z]{10}")));
    }

    @Test
    @DisplayName("frontGroups 按用户第一跳订阅的顺位排列，不按订阅 id 顺序——"
            + "取回若按 id 升序排，客户端外层 fallback 的顺序就会被打乱")
    void frontGroupsFollowAssignedSubscriptionOrder() throws Exception {
        Long airportId = fixtures.createAirport("次序测试机场");
        Long subA = fixtures.createAirportSubscription(airportId, "sub-a", 300);
        Long subB = fixtures.createAirportSubscription(airportId, "sub-b", 300);
        fixtures.createSubscriptionNode(subA, "🇺🇸[US]A-01", NodeStatus.ENABLED);
        fixtures.createSubscriptionNode(subB, "🇺🇸[US]B-01", NodeStatus.ENABLED);
        fixtures.createSubscriptionNode(subB, "🇺🇸[US]B-02", NodeStatus.ENABLED);
        // 清空 setUp 里默认分配的第一跳，改成顺位与订阅 id 升序相反：节点更多的 subB 排第一
        jdbc.update("DELETE FROM user_front_subscription WHERE user_id = ?", user1Id);
        fixtures.assignFront(user1Id, subB, subA);

        mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.frontGroups").isArray())
                .andExpect(jsonPath("$.data.frontGroups.length()").value(2))
                // subB（2 节点）排在顺位第一，subA（1 节点）排第二——
                // 取回若按订阅 id 升序排（subA 更小），这两个组的位置会颠倒
                .andExpect(jsonPath("$.data.frontGroups[0].nodes.length()").value(2))
                .andExpect(jsonPath("$.data.frontGroups[1].nodes.length()").value(1));
    }

    @Test
    @DisplayName("故障域未解析时 failureDomain 实打实下发成 null，而不是整个字段消失——"
            + "服务端没有全局 JsonInclude(NON_NULL)，客户端 DTO 必须按可空类型声明")
    void nullFailureDomainIsSerializedAsJsonNull() throws Exception {
        // 订阅刷新还没跑第一轮时，回填出来的节点也可能全都没有故障域
        Long airportId = fixtures.createAirport("空故障域机场");
        Long subId = fixtures.createAirportSubscription(airportId, "null-domain-sub", 300);
        fixtures.createMihomoNode("🇺🇸[US]Null-01", subId);
        jdbc.update("DELETE FROM user_front_subscription WHERE user_id = ?", user1Id);
        fixtures.assignFront(user1Id, subId);

        String body = mockMvc.perform(get("/api/link/config")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Device-Id", DEVICE_ID))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.frontGroups[0].nodes[0].type").value("anytls"))
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

    private long countLinkReportRows(Long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM link_report WHERE user_id = ?", Long.class, userId);
    }

    @Test
    @DisplayName("不带请求体的心跳与从前行为逐字一致，老客户端不受影响")
    void heartbeatWithoutBodyBehavesExactlyAsBefore() throws Exception {
        String body = mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.configVersion").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // 只断言 200/code=0 没有判别力：这条要连同「响应体逐字未变」与「没写库」一起断言，
        // 否则悄悄多出一个字段、或悄悄写了一行观测数据，这个测试都发现不了
        assertThat(body).matches("\\{\"code\":0,\"data\":\\{\"status\":\"ACTIVE\",\"configVersion\":\"[0-9a-f]{64}\"},\"msg\":null,\"success\":true}");
        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    @Test
    @DisplayName("带上报块的心跳落库一行，并把来源 IP 反查成 ASN")
    void heartbeatWithReportPersistsRowWithSourceAsn() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(ipAsnClient.lookup("203.0.113.9")).thenReturn(Optional.of(new AsnInfo("AS4134", "China Telecom")));

        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:00:00Z",
                  "window": {"samples": 12, "alive": 11, "noSample": 0},
                  "p50LatencyMs": 180,
                  "failovers": 1,
                  "resolvedEntryIp": "10.0.0.9"
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // X-Forwarded-For 取第一段：反查用的是真实客户端 IP，不是链路上的中间代理
        verify(ipAsnClient).lookup("203.0.113.9");

        assertThat(countLinkReportRows(user1Id)).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT failure_domain, samples, alive_count, no_sample_count, p50_latency_ms, failovers, "
                        + "resolved_entry_ip, source_asn FROM link_report WHERE user_id = ?", user1Id);
        assertThat(row.get("failure_domain")).isEqualTo("jp.tsdns.top");
        assertThat(row.get("samples")).isEqualTo(12);
        assertThat(row.get("alive_count")).isEqualTo(11);
        assertThat(row.get("no_sample_count")).isEqualTo(0);
        assertThat(row.get("p50_latency_ms")).isEqualTo(180);
        assertThat(row.get("failovers")).isEqualTo(1);
        assertThat(row.get("resolved_entry_ip")).isEqualTo("10.0.0.9");
        // ASN 由 ingest 按来源 IP 反查填上：这是「故障域 × 运营商」矩阵与运营商级告警
        // 唯一的数据来源，留 null 整个运营商维度就是空的（展示名另按 ASN 存，不进本表）
        assertThat(row.get("source_asn")).isEqualTo("AS4134");
    }

    @Test
    @DisplayName("上游给的运营商名超过 64 字符也不影响这块上报落库——名字不进 link_report，"
            + "本表只存 ASN（VARCHAR(32)），长度风险随展示名一起挪走了")
    void heartbeatWithOverlongOrgNameStillPersistsWindow() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);
        String longOrgName = "China Networks Inter-Exchange, China Telecommunications Corporation";
        when(ipAsnClient.lookup("203.0.113.9")).thenReturn(Optional.of(new AsnInfo("AS4134", longOrgName)));

        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:00:00Z",
                  "window": {"samples": 12, "alive": 11, "noSample": 0},
                  "failovers": 0
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 超长运营商名之下仍然落库一行：名字若还留在本表，超长会触发 Data too long，
        // 被 ingest 的 try/catch 吞掉，整块上报窗口静默丢弃，这里就会是 0
        assertThat(countLinkReportRows(user1Id)).isEqualTo(1);
        String sourceAsn = jdbc.queryForObject(
                "SELECT source_asn FROM link_report WHERE user_id = ?", String.class, user1Id);
        assertThat(sourceAsn).isEqualTo("AS4134");

        // 长度风险挪到了 asn_org.org_name（VARCHAR(64)）：截断后照常入库，而不是整块窗口陪葬。
        // 这条断言走的是真 MySQL，钉的是「超长名字进得了 asn_org」这条链路真的通
        // （截断本身由 AsnOrgRepositoryTest#overlongOrgNameIsTruncatedBeforeInsert 守）
        String orgName = jdbc.queryForObject(
                "SELECT org_name FROM asn_org WHERE asn = ?", String.class, "AS4134");
        assertThat(orgName).hasSize(64).isEqualTo(longOrgName.substring(0, 64));
    }

    @Test
    @DisplayName("同一 ASN 第二次心跳换了运营商文案，asn_org 仍是首次见到的那个名字")
    void heartbeatKeepsFirstSeenOrgNameForSameAsn() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(ipAsnClient.lookup("203.0.113.9"))
                .thenReturn(Optional.of(new AsnInfo("AS4134", "China Telecom")))
                .thenReturn(Optional.of(new AsnInfo("AS4134", "CHINANET-BACKBONE")));

        // 两个不同窗口，避免第二块把第一块整行覆盖掉；关心的是 asn_org 这一侧
        heartbeatWithWindow("2026-09-19T00:00:00Z");
        heartbeatWithWindow("2026-09-19T00:01:00Z");

        // 展示名的用处是让人认得出是哪家运营商，稳定比新鲜重要：上游改口径（同一家今天叫
        // China Telecom、明天叫 CHINANET-BACKBONE）不该把历史里的名字一起改掉
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT asn, org_name FROM asn_org");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("asn")).isEqualTo("AS4134");
        assertThat(rows.get(0).get("org_name")).isEqualTo("China Telecom");
    }

    /** 发一块最小可用的上报心跳，窗口起点由调用方给（同一用户同一故障域下，它就是唯一键的第三段） */
    private void heartbeatWithWindow(String windowStart) throws Exception {
        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "%s",
                  "window": {"samples": 12, "alive": 11, "noSample": 0},
                  "failovers": 0
                }
                """.formatted(windowStart);

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("failureDomain 为 null 时落库存空串")
    void nullFailureDomainStoresAsEmptyStringInDb() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);
        when(ipAsnClient.lookup(anyString())).thenReturn(Optional.empty());

        String reportJson = """
                {
                  "failureDomain": null,
                  "windowStart": "2026-09-19T00:00:00Z",
                  "window": {"samples": 5, "alive": 5, "noSample": 0},
                  "failovers": 0
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk());

        String failureDomain = jdbc.queryForObject(
                "SELECT failure_domain FROM link_report WHERE user_id = ?", String.class, user1Id);
        assertThat(failureDomain).isEqualTo("");
    }

    @Test
    @DisplayName("上报块格式有问题（残缺必填字段）不影响心跳本身返回正常结果")
    void malformedReportDoesNotBreakHeartbeat() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);

        // window 整段缺失：契约里 window.samples/alive/noSample 都不可空，
        // 但心跳绝不能因为上报块残缺而跟着失败——这里就是验证这条底线
        String malformedJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:00:00Z",
                  "failovers": 0
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    // —— C1 修复：JSON 层面的错误必须只丢一个上报窗口，不能打断整条心跳 ——
    // 这三条曾经实测复现过 code=110001（HttpMessageNotReadableException 在方法体执行前
    // 被 GlobalExceptionHandler 接住）：把 report 参数从 LinkHeartbeatRequest 改成裸
    // String、解析挪进 LinkReportService#ingest 内部的 try/catch 后，这里必须变绿

    @Test
    @DisplayName("请求体 JSON 语法错误不影响心跳本身返回正常结果，且不落库")
    void syntacticallyInvalidJsonDoesNotBreakHeartbeat() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{{{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    @Test
    @DisplayName("window 字段类型完全不匹配（字符串而不是对象）不影响心跳本身返回正常结果，且不落库")
    void fieldTypeMismatchDoesNotBreakHeartbeat() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":\"not-an-object\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    @Test
    @DisplayName("数字字段传了非数字字符串（JSON 语法合法但类型全错）不影响心跳本身返回正常结果，且不落库")
    void numericFieldWithNonNumericStringDoesNotBreakHeartbeat() throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failovers\":\"abc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    // —— I1 修复：窗口起点越界（客户端时钟不准/被构造出任意时间）时丢弃整条上报块 ——

    // —— I2 修复：上报计数不合理（alive > samples、负数）时整块丢弃 ——

    @Test
    @DisplayName("alive 大于 samples 的上报块被丢弃，心跳仍返回正常结果——不可能超过 100% 的成功率不许进库")
    void insaneCountsAreDiscardedButHeartbeatStillSucceeds() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);

        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:00:00Z",
                  "window": {"samples": 10, "alive": 11, "noSample": 0},
                  "failovers": 0
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    @Test
    @DisplayName("负样本数的上报块被丢弃：负 samples 会让告警的样本量门槛把整段判定跳过")
    void negativeSamplesAreDiscardedButHeartbeatStillSucceeds() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);

        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:00:00Z",
                  "window": {"samples": -100, "alive": -50, "noSample": 0},
                  "failovers": 0
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }

    // —— I3 修复：非 ACTIVE 用户的上报不入库 ——

    @Test
    @DisplayName("已吊销用户带上报块的心跳：仍返回 REVOKED，但上报不落库——"
            + "客户端收到 REVOKED 才断链，那一次请求带的数据不该进全库矩阵、更不该给已吊销用户推告警")
    void revokedUserReportIsNotIngested() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);

        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:00:00Z",
                  "window": {"samples": 12, "alive": 3, "noSample": 0},
                  "failovers": 1
                }
                """;

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user2Id))
                        .header("X-Forwarded-For", "203.0.113.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("REVOKED"));

        assertThat(countLinkReportRows(user2Id)).isZero();
        // 连 ASN 反查都不该发生：非 ACTIVE 用户的上报在 ingest 之前就被挡住了
        verify(ipAsnClient, never()).lookup(anyString());
    }

    @Test
    @DisplayName("窗口起点超出未来容忍范围时被丢弃，心跳仍返回正常结果")
    void futureWindowIsDiscardedButHeartbeatStillSucceeds() throws Exception {
        when(clock.instant()).thenReturn(FIXED_NOW);

        String reportJson = """
                {
                  "failureDomain": "jp.tsdns.top",
                  "windowStart": "2026-09-19T00:20:00Z",
                  "window": {"samples": 5, "alive": 5, "noSample": 0},
                  "failovers": 0
                }
                """;
        // FIXED_NOW 是 2026-09-19T00:03:00Z，窗口起点比它晚 17 分钟，超过默认 5 分钟的未来容忍

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(user1Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertThat(countLinkReportRows(user1Id)).isZero();
    }
}
