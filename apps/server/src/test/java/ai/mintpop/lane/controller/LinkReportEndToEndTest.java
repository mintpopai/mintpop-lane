package ai.mintpop.lane.controller;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.IpAsnClient.AsnInfo;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.LinkReportAlertService;
import ai.mintpop.lane.service.NodeNotifyService;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「入口到出口」整条链路：客户端上报 → 服务端反查运营商落库 → 告警扫描推运营商级告警 →
 * 管理端矩阵出现该运营商列。
 * <p>
 * 这条测试是刻意跨任务的。三期各任务的测试各自自洽——告警、归档、矩阵的测试全都直接
 * {@code report.setSourceAsn("AS4134")} 往库里塞值，ingest 的测试反过来把「运营商维度恒 null」
 * 钉成正确行为——于是「运营商维度在生产里根本没有写入来源」这个断点落在任务与任务之间，
 * **每一段单看都是绿的**。
 * 只有从真实入口（POST /api/link/heartbeat）喂进去、从真实出口（GET /api/admin/link-health）
 * 读出来，才能证明运营商维度是通的。改任何一段时这条不许绕过。
 */
@AutoConfigureMockMvc
class LinkReportEndToEndTest extends MysqlTestBase {

    /** 固定「现在」：心跳窗口容忍、告警回看窗口、管理端查询区间三处都读同一个注入的 Clock */
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private static final String DOMAIN = "jp.tsdns.top";
    private static final String SOURCE_IP = "203.0.113.9";
    private static final String ASN = "AS4134";
    /** 上游给的展示名：运营商维度的键仍是 ASN，名字只进 asn_org、只在告警文案上露面 */
    private static final String ORG_NAME = "China Telecom";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private SessionTokenService sessionTokenService;

    @Autowired
    private LinkReportAlertService linkReportAlertService;

    @MockitoBean
    private IpAsnClient ipAsnClient;

    @MockitoBean
    private NodeNotifyService notifyService;

    @MockitoBean
    private Clock clock;

    private DatabaseFixtures fixtures;
    private Long userId;
    private Long adminId;

    private String bearer(Long id) {
        return "Bearer " + sessionTokenService.issue(id, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);

        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        jdbc.execute("TRUNCATE TABLE entry_ip_history");

        userId = fixtures.createUser("u1", null);
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null);
    }

    /**
     * 默认窗口起点：比 NOW 早 3 分钟，既过得了上报的窗口容忍（windowMaxPast 默认 1 小时），
     * 也落在告警回看区间（alertLookback 默认 15 分钟）内
     */
    private static final String WINDOW_START = "2026-09-19T23:57:00Z";

    /** 成功率 50%（100 个样本、50 个存活），跌破默认阈值 0.80 且过得了最小样本量 20 */
    private String degradedReportJson() {
        return degradedReportJson(WINDOW_START);
    }

    /** 同上，但窗口起点由调用方指定——要造「同一 ASN 的多个窗口」只能靠它区分 */
    private String degradedReportJson(String windowStart) {
        return """
                {
                  "failureDomain": "%s",
                  "windowStart": "%s",
                  "window": {"samples": 100, "alive": 50, "noSample": 0},
                  "p50LatencyMs": 180,
                  "failovers": 2,
                  "resolvedEntryIp": "10.0.0.9"
                }
                """.formatted(DOMAIN, windowStart);
    }

    /** 走真实入口投一个上报窗口：来源 IP 由反代经 X-Forwarded-For 传进来 */
    private void postHeartbeat(String windowStart) throws Exception {
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(userId))
                        .header("X-Forwarded-For", SOURCE_IP + ", 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(degradedReportJson(windowStart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    private JsonNode findDomain(JsonNode domains, String failureDomain) {
        for (JsonNode domain : domains) {
            if (domain.get("failureDomain").asText().equals(failureDomain)) {
                return domain;
            }
        }
        throw new AssertionError("矩阵里没有故障域：" + failureDomain);
    }

    @Test
    @DisplayName("上报 → 反查 ASN 落库 → 运营商级告警 → 管理端矩阵出现该运营商列，整条链路是通的")
    void reportFlowsFromHeartbeatThroughAsnAlertToAdminMatrix() throws Exception {
        when(ipAsnClient.lookup(SOURCE_IP)).thenReturn(Optional.of(new AsnInfo(ASN, ORG_NAME)));

        // —— 入口：客户端心跳带上报块，来源 IP 由反代经 X-Forwarded-For 传进来 ——
        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(userId))
                        .header("X-Forwarded-For", SOURCE_IP + ", 10.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(degradedReportJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // 运营商维度真的进了库——这一列是后面两步唯一的数据来源
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_asn, samples, alive_count FROM link_report WHERE user_id = ?", userId);
        assertThat(row.get("source_asn")).isEqualTo(ASN);

        // 展示名也真的被 ingest 记进了 asn_org：这是「运营商叫什么」在生产里唯一的写入来源，
        // 少了它告警文案只能显示一串 AS 号
        assertThat(jdbc.queryForObject("SELECT org_name FROM asn_org WHERE asn = ?", String.class, ASN))
                .isEqualTo(ORG_NAME);

        // —— 中段：全库告警扫描按「故障域 × ASN」判定，推出运营商级告警，文案带上展示名 ——
        // ASN 若是 null，checkAndNotify 会跳过运营商级判定，这一条 verify 立刻变红
        linkReportAlertService.checkAll();
        verify(notifyService).notifyAsnDegraded(userId, "u1@test.example", DOMAIN, ASN, ORG_NAME, 0.5, 100L);

        // —— 出口：管理端矩阵里这个故障域下出现该运营商的列，而不是只有一行「未知运营商」 ——
        MvcResult result = mockMvc.perform(get("/api/admin/link-health")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode domains = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/domains");

        JsonNode asns = findDomain(domains, DOMAIN).get("asns");
        assertThat(asns).hasSize(1);
        JsonNode cell = asns.get(0);
        assertThat(cell.get("asn").asText()).isEqualTo(ASN);
        // 展示名也要端到端透出来：矩阵的列键是 ASN，但页面上给人看的是「China Telecom」，
        // 这个名字必须由服务端从 asn_org 反查后带出，不能让前端去猜 AS 号对应哪家
        assertThat(cell.get("orgName").asText()).isEqualTo(ORG_NAME);
        assertThat(cell.get("samples").asLong()).isEqualTo(100);
        assertThat(cell.get("aliveCount").asLong()).isEqualTo(50);
        assertThat(cell.get("successRate").asDouble()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("反查整体失败时全链路降级成「未知运营商」：矩阵仍有这个故障域，但运营商级告警不推")
    void unresolvedAsnDegradesToUnknownColumnWithoutAsnLevelAlert() throws Exception {
        when(ipAsnClient.lookup(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/link/heartbeat")
                        .header("Authorization", bearer(userId))
                        .header("X-Forwarded-For", SOURCE_IP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(degradedReportJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT source_asn FROM link_report WHERE user_id = ?", userId);
        assertThat(row.get("source_asn")).isNull();

        linkReportAlertService.checkAll();
        // 故障域级照推，运营商级跳过——未知运营商不可行动，且它的去重键会与故障域级的 (domain, "") 撞车
        verify(notifyService).notifyFailureDomainDegraded(userId, "u1@test.example", DOMAIN, 0.5, 100L);
        verify(notifyService, never())
                .notifyAsnDegraded(anyLong(), anyString(), anyString(), anyString(), any(), anyDouble(), anyLong());

        MvcResult result = mockMvc.perform(get("/api/admin/link-health")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode domains = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/domains");

        // link_report.source_asn 的 null 编码在服务层归一成空串再出到接口：空串＝「有样本但运营商未知」
        assertThat(findDomain(domains, DOMAIN).get("asns").get(0).get("asn").asText()).isEmpty();
    }

    @Test
    @DisplayName("同一 ASN 两次反查文案不同：矩阵只有一列、名字是首次那个、ASN 级告警只推一次")
    void driftingOrgNamesCollapseIntoOneColumnAndOneAlert() throws Exception {
        // 上游对同一个 ASN 的展示名会漂（今天 China Telecom、明天 CHINANET-BACKBONE）。
        // 这条盯的是「ASN 才是键、名字只是标签」这件事在整条链路上都成立：
        // 名字若参与分组，矩阵会把同一家运营商裂成两列、告警会照两个键各推一次，
        // 而两者都只在「先后看到两个不同文案」时才暴露——单窗口的测试永远发现不了。
        when(ipAsnClient.lookup(SOURCE_IP))
                .thenReturn(Optional.of(new AsnInfo(ASN, ORG_NAME)))
                .thenReturn(Optional.of(new AsnInfo(ASN, "CHINANET-BACKBONE")));

        // 两个相邻窗口，都落在告警回看区间（默认 15 分钟）内，合并后样本 200、存活 100
        postHeartbeat("2026-09-19T23:52:00Z");
        postHeartbeat("2026-09-19T23:57:00Z");

        // asn_org 是「有则不动」：第二次那个漂过的文案不许盖掉首次记下的名字
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asn_org WHERE asn = ?", Long.class, ASN))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT org_name FROM asn_org WHERE asn = ?", String.class, ASN))
                .isEqualTo(ORG_NAME);

        // 两个窗口是同一个 (domain, ASN) 分组，只判定一次、只推一条——
        // 若按名字分组，这里会变成两条不同 asn 的告警
        linkReportAlertService.checkAll();
        verify(notifyService).notifyAsnDegraded(userId, "u1@test.example", DOMAIN, ASN, ORG_NAME, 0.5, 200L);

        MvcResult result = mockMvc.perform(get("/api/admin/link-health")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode domains = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/domains");

        JsonNode asns = findDomain(domains, DOMAIN).get("asns");
        assertThat(asns).hasSize(1);
        assertThat(asns.get(0).get("asn").asText()).isEqualTo(ASN);
        assertThat(asns.get(0).get("orgName").asText()).isEqualTo(ORG_NAME);
        assertThat(asns.get(0).get("samples").asLong()).isEqualTo(200);
    }
}
