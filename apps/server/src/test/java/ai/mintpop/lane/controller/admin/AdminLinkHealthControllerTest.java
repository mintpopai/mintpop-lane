package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.entity.LinkReportDaily;
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

        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository, airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();

        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null);
        memberId = fixtures.createUser("logto-member", null);
        userA = fixtures.createUser("u1", null);
        userB = fixtures.createUser("u2", null);
    }

    private void insertRawWindow(Long userId, String domain, String asn, Instant windowStart, int samples,
                                 int aliveCount) {
        LinkReport report = new LinkReport();
        report.setUserId(userId);
        report.setFailureDomain(domain);
        report.setSourceAsn(asn);
        report.setWindowStart(windowStart);
        report.setSamples(samples);
        report.setAliveCount(aliveCount);
        report.setNoSampleCount(0);
        report.setFailovers(0);
        linkReportRepository.upsertWindow(report);
    }

    /** asn 传空串表示未知——本表 asn 是 NOT NULL DEFAULT ''，与 link_report.source_asn 的 null 编码不同 */
    private void insertDailyRow(Long userId, String domain, String asn, LocalDate statDate, long samples,
                                long aliveCount) {
        LinkReportDaily row = new LinkReportDaily();
        row.setUserId(userId);
        row.setFailureDomain(domain);
        row.setAsn(asn);
        row.setStatDate(statDate);
        row.setSamples(samples);
        row.setAliveCount(aliveCount);
        row.setNoSampleCount(0L);
        row.setFailovers(0L);
        linkReportDailyRepository.upsertDay(row);
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
    void matrixIsGroupedByDomainThenAsn() throws Exception {
        insertRawWindow(userA, "jp.tsdns.top", "AS4134", NOW.minusSeconds(60), 10, 9);
        insertRawWindow(userB, "jp.tsdns.top", "AS4837", NOW.minusSeconds(60), 20, 18);
        insertRawWindow(userA, "us.tsdns.top", "AS4134", NOW.minusSeconds(60), 5, 5);

        JsonNode body = getLinkHealth(adminId, null);
        JsonNode domains = body.at("/data/domains");
        assertThat(domains).hasSize(2);

        JsonNode jpDomain = findDomain(domains, "jp.tsdns.top");
        List<String> jpAsns = new ArrayList<>();
        jpDomain.get("asns").forEach(cell -> jpAsns.add(cell.get("asn").asText()));
        assertThat(jpAsns).containsExactlyInAnyOrder("AS4134", "AS4837");

        JsonNode usDomain = findDomain(domains, "us.tsdns.top");
        assertThat(usDomain.get("asns")).hasSize(1);
    }

    @Test
    @DisplayName("没有样本的格子 successRate 是 null 而不是 0")
    void cellWithoutSamplesHasNullRateNotZero() throws Exception {
        insertRawWindow(userA, "jp.tsdns.top", "AS4134", NOW.minusSeconds(60), 0, 0);

        JsonNode body = getLinkHealth(adminId, null);
        JsonNode cell = findDomain(body.at("/data/domains"), "jp.tsdns.top").get("asns").get(0);

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
        insertDailyRow(userA, "recent.tsdns.top", "AS4134", withinLimit, 50, 40);

        JsonNode domains = getLinkHealth(adminId, 999_999).at("/data/domains");

        List<String> domainNames = new ArrayList<>();
        domains.forEach(d -> domainNames.add(d.get("failureDomain").asText()));
        assertThat(domainNames).doesNotContain("old.tsdns.top");
        assertThat(domainNames).contains("recent.tsdns.top");
    }

    @Test
    @DisplayName("格子端到端按样本量倒序、同数按 ASN 升序：合并两表后的总量决定顺序，而不是任一表内的量或 ASN 字典序")
    void matrixCellsAreOrderedBySamplesDescEndToEnd() throws Exception {
        int rawRetentionDays = linkReportProperties.getRawRetentionDays();
        LocalDate olderDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(rawRetentionDays + 1L);
        // AS9808 原始表里最少（2），靠按天表的 50 合计 52 登顶——只看原始表会把它排到最后
        insertRawWindow(userA, "jp.tsdns.top", "AS9808", NOW.minusSeconds(60), 2, 2);
        insertDailyRow(userA, "jp.tsdns.top", "AS9808", olderDate, 50, 40);
        // AS4837 与 AS4134 同为 20：并列时按 ASN 串升序，AS4134 在前
        insertRawWindow(userB, "jp.tsdns.top", "AS4837", NOW.minusSeconds(60), 20, 18);
        insertRawWindow(userA, "jp.tsdns.top", "AS4134", NOW.minusSeconds(120), 20, 19);
        // AS1 字典序最小但样本最少，必须排在最后。窗口错开：upsertWindow 按「用户×窗口」去重，
        // 同一用户同一窗口再写一条会覆盖上面 AS4837 那条
        insertRawWindow(userB, "jp.tsdns.top", "AS1", NOW.minusSeconds(180), 3, 3);

        JsonNode cells = findDomain(getLinkHealth(adminId, rawRetentionDays + 5).at("/data/domains"),
                "jp.tsdns.top").get("asns");
        List<String> order = new ArrayList<>();
        cells.forEach(cell -> order.add(cell.get("asn").asText()));

        assertThat(order).containsExactly("AS9808", "AS4134", "AS4837", "AS1");
    }

    @Test
    @DisplayName("原始表与按天聚合表里同一故障域×运营商的数据会合并求和")
    void matrixCombinesRawAndDailyTables() throws Exception {
        int rawRetentionDays = linkReportProperties.getRawRetentionDays();
        insertRawWindow(userA, "jp.tsdns.top", "AS4134", NOW.minusSeconds(60), 10, 9);
        LocalDate olderDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(rawRetentionDays + 1L);
        insertDailyRow(userA, "jp.tsdns.top", "AS4134", olderDate, 100, 80);

        JsonNode cell = findDomain(getLinkHealth(adminId, rawRetentionDays + 5).at("/data/domains"),
                "jp.tsdns.top").get("asns").get(0);

        assertThat(cell.get("samples").asLong()).isEqualTo(110);
        assertThat(cell.get("aliveCount").asLong()).isEqualTo(89);
    }

    @Test
    @DisplayName("两张表对「运营商未知」的不同编码（null / 空串）端到端合并成同一个格子，不会裂成两行")
    void unresolvedAsnFromBothTablesEndToEndMergesIntoOneCell() throws Exception {
        int rawRetentionDays = linkReportProperties.getRawRetentionDays();
        // link_report.source_asn 传 null——反查失败的真实编码
        insertRawWindow(userA, "jp.tsdns.top", null, NOW.minusSeconds(60), 10, 9);
        // link_report_daily.asn 传空串——同样是反查失败，但本表的真实编码是 NOT NULL DEFAULT ''
        LocalDate olderDate = LocalDate.ofInstant(NOW, ZoneOffset.UTC).minusDays(rawRetentionDays + 1L);
        insertDailyRow(userA, "jp.tsdns.top", "", olderDate, 5, 4);

        JsonNode asns = findDomain(getLinkHealth(adminId, rawRetentionDays + 5).at("/data/domains"),
                "jp.tsdns.top").get("asns");

        assertThat(asns).hasSize(1);
        JsonNode cell = asns.get(0);
        assertThat(cell.get("asn").asText()).isEqualTo("");
        assertThat(cell.get("samples").asLong()).isEqualTo(15);
        assertThat(cell.get("aliveCount").asLong()).isEqualTo(13);
    }

    @Test
    @DisplayName("行数是「故障域×ASN」分组数，不是原始窗口行数——聚合真的落在 SQL 层")
    void aggregationCountReflectsGroupsNotRawRows() throws Exception {
        // 3 条原始窗口（2 个用户），但只有 2 个「故障域×ASN」分组：
        // 若实现退化成把某个用户的原始行拉回内存再分组、漏了别的用户，行数会不对
        insertRawWindow(userA, "jp.tsdns.top", "AS4134", NOW.minusSeconds(60), 10, 9);
        insertRawWindow(userB, "jp.tsdns.top", "AS4134", NOW.minusSeconds(120), 20, 18);
        insertRawWindow(userB, "us.tsdns.top", "AS4837", NOW.minusSeconds(60), 5, 5);

        JsonNode domains = getLinkHealth(adminId, null).at("/data/domains");
        assertThat(domains).hasSize(2);

        JsonNode jpCell = findDomain(domains, "jp.tsdns.top").get("asns").get(0);
        assertThat(jpCell.get("samples").asLong()).isEqualTo(30);
        assertThat(jpCell.get("aliveCount").asLong()).isEqualTo(27);
    }

    @Test
    @DisplayName("故障域为空串（尚未解析）时原样透出，不被吞掉")
    void unresolvedFailureDomainIsPreservedAsEmptyString() throws Exception {
        insertRawWindow(userA, "", "AS4134", NOW.minusSeconds(60), 5, 5);

        JsonNode domains = getLinkHealth(adminId, null).at("/data/domains");
        assertThat(findDomain(domains, "")).isNotNull();
    }

}
