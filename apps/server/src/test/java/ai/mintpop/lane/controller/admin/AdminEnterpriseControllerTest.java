package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.repository.EnterpriseRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.service.SessionTokenService;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminEnterpriseControllerTest extends MysqlTestBase {

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

    /** 竞态测试里屏蔽重名预检查用；其余测试不打桩，行为与真实 bean 一致 */
    @MockitoSpyBean
    private EnterpriseRepository enterpriseRepository;

    private DatabaseFixtures fixtures;

    private Long adminId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 一份合法的企业入参，调用方按需覆盖个别字段 */
    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Acme 科技");
        body.put("domain", "acme.com");
        body.put("agentTypes", List.of("CLAUDE", "CODEX"));
        body.put("enabled", true);
        body.put("remark", "首批客户");
        return body;
    }

    private Long createEnterprise(Map<String, Object> body) throws Exception {
        var result = mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
    }

    @Test
    @DisplayName("创建企业后列表回显全部字段，多个企业按 id 升序")
    void createAndList() throws Exception {
        Long firstId = createEnterprise(validBody());
        Map<String, Object> second = validBody();
        second.put("name", "Globex");
        second.put("domain", "globex.io");
        second.put("agentTypes", List.of("CODEX"));
        second.put("enabled", false);
        second.put("remark", "");
        createEnterprise(second);

        mockMvc.perform(get("/api/admin/enterprises").header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].name").value("Acme 科技"))
                .andExpect(jsonPath("$.data[0].domain").value("acme.com"))
                .andExpect(jsonPath("$.data[0].agentTypes.length()").value(2))
                .andExpect(jsonPath("$.data[0].agentTypes[0]").value("CLAUDE"))
                .andExpect(jsonPath("$.data[0].agentTypes[1]").value("CODEX"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].remark").value("首批客户"))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.data[0].updatedAt").exists())
                .andExpect(jsonPath("$.data[1].name").value("Globex"))
                .andExpect(jsonPath("$.data[1].agentTypes.length()").value(1))
                .andExpect(jsonPath("$.data[1].agentTypes[0]").value("CODEX"))
                .andExpect(jsonPath("$.data[1].enabled").value(false));
    }

    @Test
    @DisplayName("域名一律小写入库：大写混写的入参回显为小写")
    void domainNormalizedToLowerCase() throws Exception {
        Map<String, Object> body = validBody();
        body.put("domain", "ACME.Com");
        createEnterprise(body);

        mockMvc.perform(get("/api/admin/enterprises").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].domain").value("acme.com"));
    }

    @Test
    @DisplayName("企业重名报 410021 且不落库")
    void duplicateNameRejected() throws Exception {
        createEnterprise(validBody());

        Map<String, Object> sameName = validBody();
        sameName.put("domain", "acme-cn.com");
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(sameName)))
                .andExpect(jsonPath("$.code").value(410021));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("企业域名重复报 410022，大小写不同也算重复")
    void duplicateDomainRejected() throws Exception {
        createEnterprise(validBody());

        Map<String, Object> sameDomain = validBody();
        sameDomain.put("name", "Acme 中国");
        sameDomain.put("domain", "ACME.COM");
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(sameDomain)))
                .andExpect(jsonPath("$.code").value(410022));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("参数校验：名称必填、域名必填且须是合法域名、agent 类型至少选一个，均报 110001")
    void validationFailures() throws Exception {
        Map<String, Object> blankName = validBody();
        blankName.put("name", " ");
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(blankName)))
                .andExpect(jsonPath("$.code").value(110001));

        Map<String, Object> blankDomain = validBody();
        blankDomain.put("domain", " ");
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(blankDomain)))
                .andExpect(jsonPath("$.code").value(110001));

        Map<String, Object> badDomain = validBody();
        badDomain.put("domain", "https://acme.com/path");
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(badDomain)))
                .andExpect(jsonPath("$.code").value(110001));

        Map<String, Object> noAgentTypes = validBody();
        noAgentTypes.put("agentTypes", List.of());
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(noAgentTypes)))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("更新覆盖全部字段（含停用）；企业不存在报 410020；改名撞已有企业报 410021")
    void updateEnterprise() throws Exception {
        Long enterpriseId = createEnterprise(validBody());
        Map<String, Object> other = validBody();
        other.put("name", "Globex");
        other.put("domain", "globex.io");
        createEnterprise(other);

        Map<String, Object> updated = validBody();
        updated.put("name", "Acme 科技（新）");
        updated.put("domain", "acme.dev");
        updated.put("agentTypes", List.of("CODEX"));
        updated.put("enabled", false);
        updated.put("remark", "改签后");
        mockMvc.perform(put("/api/admin/enterprises/" + enterpriseId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(updated)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/enterprises").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("Acme 科技（新）"))
                .andExpect(jsonPath("$.data[0].domain").value("acme.dev"))
                .andExpect(jsonPath("$.data[0].agentTypes.length()").value(1))
                .andExpect(jsonPath("$.data[0].agentTypes[0]").value("CODEX"))
                .andExpect(jsonPath("$.data[0].enabled").value(false))
                .andExpect(jsonPath("$.data[0].remark").value("改签后"));

        mockMvc.perform(put("/api/admin/enterprises/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(jsonPath("$.code").value(410020));

        Map<String, Object> collision = validBody();
        collision.put("name", "Globex");
        mockMvc.perform(put("/api/admin/enterprises/" + enterpriseId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(collision)))
                .andExpect(jsonPath("$.code").value(410021));
    }

    @Test
    @DisplayName("只改大小写的改名不被表的 ci 排序规则误判为重名")
    void renameCaseOnlyChangeSucceeds() throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", "Acme Inc");
        Long enterpriseId = createEnterprise(body);

        Map<String, Object> renamed = validBody();
        renamed.put("name", "ACME INC");
        mockMvc.perform(put("/api/admin/enterprises/" + enterpriseId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(renamed)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/enterprises").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("ACME INC"));
    }

    @Test
    @DisplayName("重名预检查与插入之间的竞态由唯一索引兜底，映射为 410021 而非内部错误")
    void duplicateNameRaceFallsBackToUniqueIndex() throws Exception {
        createEnterprise(validBody());

        // 模拟两个管理员同时提交、双双通过预检查的间隙：把预检查打成「不存在」，
        // 让插入直接撞数据库唯一索引
        doReturn(false).when(enterpriseRepository).existsByName(anyString());
        doReturn(false).when(enterpriseRepository).existsByDomain(anyString());
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(jsonPath("$.code").value(410021));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("域名竞态撞唯一索引时报的是域名重复 410022，不会错报成重名")
    void duplicateDomainRaceReportsDomainCode() throws Exception {
        createEnterprise(validBody());

        Map<String, Object> sameDomainOnly = validBody();
        sameDomainOnly.put("name", "Acme 中国");
        doReturn(false).when(enterpriseRepository).existsByDomain(anyString());
        mockMvc.perform(post("/api/admin/enterprises").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(sameDomainOnly)))
                .andExpect(jsonPath("$.code").value(410022));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("删除企业后列表不再出现；删除不存在的企业报 410020")
    void deleteEnterprise() throws Exception {
        Long enterpriseId = createEnterprise(validBody());

        mockMvc.perform(delete("/api/admin/enterprises/" + enterpriseId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/enterprises").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(delete("/api/admin/enterprises/" + enterpriseId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410020));
    }

    @Test
    @DisplayName("企业仍被订阅引用时拒绝删除，报 410025")
    void deleteRejectedWhenReferencedBySubscription() throws Exception {
        Long enterpriseId = createEnterprise(validBody());
        Long userId = fixtures.createUser("logto-member", null, null);
        Long subscriptionId = fixtures.createSubscription(userId, AgentType.CLAUDE, "Claude 席位",
                Instant.now(), Instant.now().plus(30, ChronoUnit.DAYS), null);
        jdbc.update("UPDATE subscription SET enterprise_id = ? WHERE id = ?", enterpriseId, subscriptionId);

        mockMvc.perform(delete("/api/admin/enterprises/" + enterpriseId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410025));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM enterprise", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
