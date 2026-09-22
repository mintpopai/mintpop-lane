package ai.mintpop.lane.controller;

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
    /** 上游给的展示名：只喂给反查桩，运营商维度的键是 ASN，名字不参与分组与去重 */
    private static final String ORG_NAME = "China Telecom";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        jdbc.execute("TRUNCATE TABLE entry_ip_history");

        userId = fixtures.createUser("u1", null, null);
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
    }

    /** 成功率 50%（100 个样本、50 个存活），跌破默认阈值 0.80 且过得了最小样本量 20 */
    private String degradedReportJson() {
        return """
                {
                  "failureDomain": "%s",
                  "windowStart": "2026-09-19T23:57:00Z",
                  "window": {"samples": 100, "alive": 50, "noSample": 0},
                  "p50LatencyMs": 180,
                  "failovers": 2,
                  "resolvedEntryIp": "10.0.0.9"
                }
                """.formatted(DOMAIN);
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

        // —— 中段：全库告警扫描按「故障域 × ASN」判定，推出运营商级告警 ——
        // ASN 若是 null，checkAndNotify 会跳过运营商级判定，这一条 verify 立刻变红
        linkReportAlertService.checkAll();
        verify(notifyService).notifyIspDegraded(userId, "u1@test.example", DOMAIN, ASN, 0.5, 100L);

        // —— 出口：管理端矩阵里这个故障域下出现该运营商的列，而不是只有一行「未知运营商」 ——
        MvcResult result = mockMvc.perform(get("/api/admin/link-health")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode domains = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/domains");

        JsonNode isps = findDomain(domains, DOMAIN).get("isps");
        assertThat(isps).hasSize(1);
        JsonNode cell = isps.get(0);
        assertThat(cell.get("isp").asText()).isEqualTo(ASN);
        assertThat(cell.get("samples").asLong()).isEqualTo(100);
        assertThat(cell.get("aliveCount").asLong()).isEqualTo(50);
        assertThat(cell.get("successRate").asDouble()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("反查整体失败时全链路降级成「未知运营商」：矩阵仍有这个故障域，但运营商级告警不推")
    void unresolvedAsnDegradesToUnknownColumnWithoutIspLevelAlert() throws Exception {
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
                .notifyIspDegraded(anyLong(), anyString(), anyString(), anyString(), anyDouble(), anyLong());

        MvcResult result = mockMvc.perform(get("/api/admin/link-health")
                        .header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode domains = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/domains");

        // link_report.source_asn 的 null 编码在服务层归一成空串再出到接口：空串＝「有样本但运营商未知」
        assertThat(findDomain(domains, DOMAIN).get("isps").get(0).get("isp").asText()).isEmpty();
    }
}
