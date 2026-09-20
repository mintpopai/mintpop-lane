package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.entity.LinkReportDaily;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.repository.LinkReportDailyRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端链路健康查询接口的 MockMvc 集成测试：路由是否真的接上、鉴权
 * （/api/admin/** → ROLE_ADMIN）是否真的把非管理员挡在外面、`days` 查询参数能否正确绑定——
 * 这几处接线本身可能出错的地方，{@link ai.mintpop.lane.service.AdminLinkHealthServiceImplTest}
 * 的纯 Mockito 单测覆盖不到。业务逻辑本身（聚合、合并、successRate 语义、时间线边界）
 * 由那份单测覆盖，这里只挑关键场景各留一条做端到端验证。
 */
@AutoConfigureMockMvc
class AdminLinkHealthControllerTest extends MysqlTestBase {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

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
    private LinkReportRepository linkReportRepository;

    @Autowired
    private LinkReportDailyRepository linkReportDailyRepository;

    @Autowired
    private LinkReportProperties linkReportProperties;

    @MockitoBean
    private Clock clock;

    private DatabaseFixtures fixtures;
    private Long adminId;
    private Long memberId;
    private Long userA;
    private Long userB;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);

        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        // entry_ip_history 不是用户维度的表，clearAll 不清它，这里单独清空以隔离各用例
        jdbc.execute("TRUNCATE TABLE entry_ip_history");

        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
        memberId = fixtures.createUser("logto-member", null, null);
        userA = fixtures.createUser("u1", null, null);
        userB = fixtures.createUser("u2", null, null);
    }

    private void insertRawWindow(Long userId, String domain, String isp, Instant windowStart, int samples,
                                 int aliveCount) {
        LinkReport report = new LinkReport();
        report.setUserId(userId);
        report.setFailureDomain(domain);
        report.setIsp(isp);
        report.setWindowStart(windowStart);
        report.setSamples(samples);
        report.setAliveCount(aliveCount);
        report.setNoSampleCount(0);
        report.setFailovers(0);
        linkReportRepository.upsertWindow(report);
    }

    /** isp 传空串表示未知——本表 isp 是 NOT NULL DEFAULT ''，与 link_report 的 null 编码不同 */
    private void insertDailyRow(Long userId, String domain, String isp, LocalDate statDate, long samples,
                                long aliveCount) {
        LinkReportDaily row = new LinkReportDaily();
        row.setUserId(userId);
        row.setFailureDomain(domain);
        row.setIsp(isp);
        row.setStatDate(statDate);
        row.setSamples(samples);
        row.setAliveCount(aliveCount);
        row.setNoSampleCount(0L);
        row.setFailovers(0L);
        linkReportDailyRepository.upsertDay(row);
    }

    /** 应用层永不写 observed_at（数据库默认值维护），测试要控制观测时刻只能绕开 mapper 直接插入 */
    private void insertEntryIpHistory(String domain, DnsVantage vantage, String entryIps, Instant observedAt) {
        jdbc.update("INSERT INTO entry_ip_history (failure_domain, vantage, entry_ips, observed_at) "
                + "VALUES (?, ?, ?, ?)", domain, vantage.name(), entryIps, Timestamp.from(observedAt));
    }

    private JsonNode getLinkHealth(Long callerId, Integer days) throws Exception {
        var requestBuilder = get("/api/admin/link-health").header("Authorization", bearer(callerId));
        if (days != null) {
            requestBuilder = requestBuilder.param("days", String.valueOf(days));
        }
        MvcResult result = mockMvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findDomain(JsonNode domains, String failureDomain) {
        for (JsonNode domain : domains) {
            if (domain.get("failureDomain").asText().equals(failureDomain)) {
                return domain;
            }
        }
        throw new AssertionError("domain not found in response: " + failureDomain);
    }

    @Test
    @DisplayName("矩阵按故障域分组，每组下按运营商展开")
    void matrixIsGroupedByDomainThenIsp() throws Exception {
        insertRawWindow(userA, "jp.tsdns.top", "CTC", NOW.minusSeconds(60), 10, 9);
        insertRawWindow(userB, "jp.tsdns.top", "CUCC", NOW.minusSeconds(60), 20, 18);
        insertRawWindow(userA, "us.tsdns.top", "CTC", NOW.minusSeconds(60), 5, 5);

        JsonNode body = getLinkHealth(adminId, null);
        JsonNode domains = body.at("/data/domains");
        assertThat(domains).hasSize(2);

        JsonNode jpDomain = findDomain(domains, "jp.tsdns.top");
        List<String> jpIsps = new ArrayList<>();
        jpDomain.get("isps").forEach(cell -> jpIsps.add(cell.get("isp").asText()));
        assertThat(jpIsps).containsExactlyInAnyOrder("CTC", "CUCC");

        JsonNode usDomain = findDomain(domains, "us.tsdns.top");
        assertThat(usDomain.get("isps")).hasSize(1);
    }

    @Test
    @DisplayName("没有样本的格子 successRate 是 null 而不是 0")
    void cellWithoutSamplesHasNullRateNotZero() throws Exception {
        insertRawWindow(userA, "jp.tsdns.top", "CTC", NOW.minusSeconds(60), 0, 0);

        JsonNode body = getLinkHealth(adminId, null);
        JsonNode cell = findDomain(body.at("/data/domains"), "jp.tsdns.top").get("isps").get(0);

        assertThat(cell.get("samples").asLong()).isZero();
        assertThat(cell.get("successRate").isNull())
                .as("samples 为 0 时 successRate 必须是 JSON null，不能是 0 或 0.0")
                .isTrue();
    }

    @Test
    @DisplayName("非管理员访问被拒")
    void nonAdminIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/link-health").header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("days 参数超出范围时收敛到上限，不拖垮库")
    void daysParamIsClamped() throws Exception {
        int maxDays = linkReportProperties.getDailyRetentionDays();
        // 直接插一行"理论上早该被归档任务清理掉"的按天聚合行，落在收敛上限之外，
        // 只有 clamp 真的生效（把查询区间收窄到上限内）才不会被查出来
        LocalDate beyondLimit = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(maxDays + 30L);
        insertDailyRow(userA, "old.tsdns.top", "", beyondLimit, 100, 100);

        // 落在收敛上限之内的正常数据，应该照常查得到
        LocalDate withinLimit = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(maxDays - 1L);
        insertDailyRow(userA, "recent.tsdns.top", "CTC", withinLimit, 50, 40);

        JsonNode domains = getLinkHealth(adminId, 999_999).at("/data/domains");

        List<String> domainNames = new ArrayList<>();
        domains.forEach(d -> domainNames.add(d.get("failureDomain").asText()));
        assertThat(domainNames).doesNotContain("old.tsdns.top");
        assertThat(domainNames).contains("recent.tsdns.top");
    }

    @Test
    @DisplayName("原始表与按天聚合表里同一故障域×运营商的数据会合并求和")
    void matrixCombinesRawAndDailyTables() throws Exception {
        int rawRetentionDays = linkReportProperties.getRawRetentionDays();
        insertRawWindow(userA, "jp.tsdns.top", "CTC", NOW.minusSeconds(60), 10, 9);
        LocalDate olderDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(rawRetentionDays + 1L);
        insertDailyRow(userA, "jp.tsdns.top", "CTC", olderDate, 100, 80);

        JsonNode cell = findDomain(getLinkHealth(adminId, rawRetentionDays + 5).at("/data/domains"),
                "jp.tsdns.top").get("isps").get(0);

        assertThat(cell.get("samples").asLong()).isEqualTo(110);
        assertThat(cell.get("aliveCount").asLong()).isEqualTo(89);
    }

    @Test
    @DisplayName("两张表对「运营商未知」的不同编码（null / 空串）端到端合并成同一个格子，不会裂成两行")
    void unresolvedIspFromBothTablesEndToEndMergesIntoOneCell() throws Exception {
        int rawRetentionDays = linkReportProperties.getRawRetentionDays();
        // link_report.isp 传 null——ASN 反查失败的真实编码
        insertRawWindow(userA, "jp.tsdns.top", null, NOW.minusSeconds(60), 10, 9);
        // link_report_daily.isp 传空串——同样是反查失败，但本表的真实编码是 NOT NULL DEFAULT ''
        LocalDate olderDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(rawRetentionDays + 1L);
        insertDailyRow(userA, "jp.tsdns.top", "", olderDate, 5, 4);

        JsonNode isps = findDomain(getLinkHealth(adminId, rawRetentionDays + 5).at("/data/domains"),
                "jp.tsdns.top").get("isps");

        assertThat(isps).hasSize(1);
        JsonNode cell = isps.get(0);
        assertThat(cell.get("isp").asText()).isEqualTo("");
        assertThat(cell.get("samples").asLong()).isEqualTo(15);
        assertThat(cell.get("aliveCount").asLong()).isEqualTo(13);
    }

    @Test
    @DisplayName("行数是「故障域×运营商」分组数，不是原始窗口行数——聚合真的落在 SQL 层")
    void aggregationCountReflectsGroupsNotRawRows() throws Exception {
        // 3 条原始窗口（2 个用户），但只有 2 个「故障域×运营商」分组：
        // 若实现退化成把某个用户的原始行拉回内存再分组、漏了别的用户，行数会不对
        insertRawWindow(userA, "jp.tsdns.top", "CTC", NOW.minusSeconds(60), 10, 9);
        insertRawWindow(userB, "jp.tsdns.top", "CTC", NOW.minusSeconds(120), 20, 18);
        insertRawWindow(userB, "us.tsdns.top", "CUCC", NOW.minusSeconds(60), 5, 5);

        JsonNode domains = getLinkHealth(adminId, null).at("/data/domains");
        assertThat(domains).hasSize(2);

        JsonNode jpCell = findDomain(domains, "jp.tsdns.top").get("isps").get(0);
        assertThat(jpCell.get("samples").asLong()).isEqualTo(30);
        assertThat(jpCell.get("aliveCount").asLong()).isEqualTo(27);
    }

    @Test
    @DisplayName("故障域为空串（尚未解析）时原样透出，不被吞掉")
    void unresolvedFailureDomainIsPreservedAsEmptyString() throws Exception {
        insertRawWindow(userA, "", "CTC", NOW.minusSeconds(60), 5, 5);

        JsonNode domains = getLinkHealth(adminId, null).at("/data/domains");
        assertThat(findDomain(domains, "")).isNotNull();
    }

    @Test
    @DisplayName("入口 IP 变更时间线：只报窗口内的变更，且带上变更前后的 IP 与视角")
    void entryIpTimelineReportsChangesWithinWindow() throws Exception {
        insertEntryIpHistory("jp.tsdns.top", DnsVantage.OVERSEAS, "1.1.1.1", NOW.minus(Duration.ofDays(3)));
        insertEntryIpHistory("jp.tsdns.top", DnsVantage.OVERSEAS, "2.2.2.2", NOW.minus(Duration.ofDays(1)));

        JsonNode timeline = getLinkHealth(adminId, 7).at("/data/entryIpTimeline");

        assertThat(timeline).hasSize(1);
        JsonNode change = timeline.get(0);
        assertThat(change.get("failureDomain").asText()).isEqualTo("jp.tsdns.top");
        assertThat(change.get("vantage").asText()).isEqualTo("OVERSEAS");
        assertThat(change.get("previousIps").asText()).isEqualTo("1.1.1.1");
        assertThat(change.get("currentIps").asText()).isEqualTo("2.2.2.2");
    }
}
