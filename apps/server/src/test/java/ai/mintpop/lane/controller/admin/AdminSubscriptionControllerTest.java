package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.entity.Enterprise;
import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.repository.EnterpriseRepository;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserRole.MEMBER;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminSubscriptionControllerTest extends MysqlTestBase {

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
    private PlanRepository planRepository;

    @Autowired
    private EnterpriseRepository enterpriseRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    private DatabaseFixtures fixtures;
    private Long adminId;
    private Long memberId;
    private Long monthlyPlanId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 可变 Map：部分用例要留空某字段，Map.of 不允许放 null。agent 类型不入参，由所选套餐决定 */
    private Map<String, Object> createRequest(Long planId, String startsAt, String credential) {
        Map<String, Object> body = new HashMap<>();
        body.put("planId", planId);
        body.put("startsAt", startsAt);
        body.put("credential", credential);
        return body;
    }

    private Map<String, Object> updateRequest(String startsAt, String credential, String remark) {
        Map<String, Object> body = new HashMap<>();
        body.put("startsAt", startsAt);
        body.put("credential", credential);
        body.put("remark", remark);
        return body;
    }

    private Long createPlan(String name, AgentType agentType, int durationDays, String price, boolean enabled) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setAgentType(agentType);
        plan.setDurationDays(durationDays);
        plan.setPrice(new BigDecimal(price));
        plan.setCurrency(Currency.USD);
        plan.setEnabled(enabled);
        return planRepository.create(plan);
    }

    private Long createEnterprise(String name, String domain, boolean enabled, AgentType... agentTypes) {
        Enterprise enterprise = new Enterprise();
        enterprise.setName(name);
        enterprise.setDomain(domain);
        enterprise.setAgentTypes(List.of(agentTypes));
        enterprise.setEnabled(enabled);
        return enterpriseRepository.create(enterprise);
    }

    private Long createSubscription(Long userId, Map<String, Object> body) throws Exception {
        String response = mockMvc.perform(post("/api/admin/users/" + userId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").asLong();
    }

    private JsonNode listSubscriptions(Long userId) throws Exception {
        String response = mockMvc.perform(get("/api/admin/users/" + userId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        Long frontId = fixtures.createFrontNode("FRONT-1");
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, frontId, null);
        memberId = fixtures.createUser("logto-member", MEMBER, ACTIVE, frontId, null);
        monthlyPlanId = createPlan("Claude 月付", AgentType.CLAUDE, 30, "99.99", true);
    }

    @Test
    @DisplayName("从套餐分配：名称/时长/价格快照落到订阅上，止期自动算，分配号是 32 位十六进制")
    void createFromPlanSnapshotsAndComputesEnd() throws Exception {
        String body = json(createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", "sk-ant-x"));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isNumber());

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].agentType").value("CLAUDE"))
                .andExpect(jsonPath("$.data[0].name").value("Claude 月付"))
                .andExpect(jsonPath("$.data[0].planId").value(monthlyPlanId))
                .andExpect(jsonPath("$.data[0].planDurationDays").value(30))
                .andExpect(jsonPath("$.data[0].planPrice").value(99.99))
                .andExpect(jsonPath("$.data[0].planCurrency").value("USD"))
                .andExpect(jsonPath("$.data[0].assignmentNo", matchesPattern("[0-9a-f]{32}")))
                .andExpect(jsonPath("$.data[0].startsAt", containsString("2026-08-01T00:00:00")))
                .andExpect(jsonPath("$.data[0].endsAt", containsString("2026-08-31T00:00:00")))
                .andExpect(jsonPath("$.data[0].hasCredential").value(true))
                .andExpect(content().string(not(containsString("sk-ant-x"))));
    }

    @Test
    @DisplayName("agent 类型来自所选套餐：分配 Codex 套餐得到 CODEX 订阅")
    void agentTypeComesFromPlan() throws Exception {
        Long codexPlanId = createPlan("Codex 月付", AgentType.CODEX, 30, "49.99", true);
        createSubscription(memberId, createRequest(codexPlanId, "2026-08-01T00:00:00Z", null));

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].agentType").value("CODEX"));
    }

    @Test
    @DisplayName("不传起期时默认取当前时刻，止期仍按套餐时长推算")
    void createWithoutStartsAtDefaultsToNow() throws Exception {
        createSubscription(memberId, createRequest(monthlyPlanId, null, null));

        JsonNode row = listSubscriptions(memberId).get(0);
        Instant startsAt = Instant.parse(row.get("startsAt").asText());
        Instant endsAt = Instant.parse(row.get("endsAt").asText());
        assertThat(Duration.between(startsAt, endsAt)).isEqualTo(Duration.ofDays(30));
        assertThat(Duration.between(startsAt, Instant.now()).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("每次分配的分配号互不相同")
    void eachAssignmentGetsDistinctNo() throws Exception {
        createSubscription(memberId, createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));
        createSubscription(memberId, createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));

        JsonNode list = listSubscriptions(memberId);
        assertThat(list.get(0).get("assignmentNo").asText())
                .isNotEqualTo(list.get(1).get("assignmentNo").asText());
    }

    @Test
    @DisplayName("套餐后续改名改价改 agent 类型都不影响已分配订阅的快照")
    void planChangesDoNotAffectSnapshot() throws Exception {
        createSubscription(memberId, createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));

        Plan plan = planRepository.findById(monthlyPlanId).orElseThrow();
        plan.setName("Claude 月付（涨价后）");
        plan.setAgentType(AgentType.CODEX);
        plan.setPrice(new BigDecimal("199.99"));
        planRepository.update(plan);

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("Claude 月付"))
                .andExpect(jsonPath("$.data[0].agentType").value("CLAUDE"))
                .andExpect(jsonPath("$.data[0].planPrice").value(99.99));
    }

    @Test
    @DisplayName("选不存在的套餐报 410017")
    void createWithMissingPlan() throws Exception {
        String body = json(createRequest(99999L, "2026-08-01T00:00:00Z", null));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(410017));
    }

    @Test
    @DisplayName("选已停用的套餐报 410019")
    void createWithDisabledPlan() throws Exception {
        Long disabledId = createPlan("已下架套餐", AgentType.CLAUDE, 30, "9.99", false);
        String body = json(createRequest(disabledId, "2026-08-01T00:00:00Z", null));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(410019));
    }

    @Test
    @DisplayName("不传套餐报 110001")
    void createWithoutPlanFailsValidation() throws Exception {
        String body = json(createRequest(null, "2026-08-01T00:00:00Z", null));

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("给不存在的用户建订阅报 410006")
    void createSubscriptionForMissingUser() throws Exception {
        String body = json(createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));

        mockMvc.perform(post("/api/admin/users/99999/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(410006));
    }

    @Test
    @DisplayName("更新只改起期/凭据/备注：止期按快照时长重算，套餐与名称不动，留空凭据沿用原值")
    void updateRecomputesEndAndKeepsPlan() throws Exception {
        Long id = createSubscription(memberId,
                createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", "sk-ant-x"));

        String update = json(updateRequest("2026-09-01T00:00:00Z", null, "顺延一个月"));
        mockMvc.perform(put("/api/admin/subscriptions/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("Claude 月付"))
                .andExpect(jsonPath("$.data[0].planId").value(monthlyPlanId))
                .andExpect(jsonPath("$.data[0].startsAt", containsString("2026-09-01T00:00:00")))
                .andExpect(jsonPath("$.data[0].endsAt", containsString("2026-10-01T00:00:00")))
                .andExpect(jsonPath("$.data[0].remark").value("顺延一个月"))
                .andExpect(jsonPath("$.data[0].hasCredential").value(true));
    }

    @Test
    @DisplayName("更新不改变分配号")
    void updateKeepsAssignmentNo() throws Exception {
        Long id = createSubscription(memberId,
                createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));
        String before = listSubscriptions(memberId).get(0).get("assignmentNo").asText();

        String update = json(updateRequest("2026-09-01T00:00:00Z", null, null));
        mockMvc.perform(put("/api/admin/subscriptions/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(listSubscriptions(memberId).get(0).get("assignmentNo").asText()).isEqualTo(before);
    }

    @Test
    @DisplayName("更新不存在的订阅报 410008")
    void updateMissingSubscription() throws Exception {
        String update = json(updateRequest("2026-08-01T00:00:00Z", null, null));

        mockMvc.perform(put("/api/admin/subscriptions/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(jsonPath("$.code").value(410008));
    }

    @Test
    @DisplayName("删除不存在的订阅报 410008")
    void deleteMissingSubscription() throws Exception {
        mockMvc.perform(delete("/api/admin/subscriptions/99999").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410008));
    }

    @Test
    @DisplayName("删除订阅后列表变空")
    void deleteSubscription() throws Exception {
        Long id = createSubscription(memberId,
                createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", "sk-ant-x"));

        mockMvc.perform(delete("/api/admin/subscriptions/" + id).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("MEMBER 访问订阅接口得 403")
    void memberAccessGets403() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(memberId)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("止期恒为起期加套餐天数：跨月场景 31 天套餐也不漂移")
    void endDateFollowsPlanDurationAcrossMonths() throws Exception {
        Long planId = createPlan("31 天套餐", AgentType.CLAUDE, 31, "10.00", true);
        createSubscription(memberId, createRequest(planId, "2026-01-15T08:30:00Z", null));

        JsonNode row = listSubscriptions(memberId).get(0);
        assertThat(Instant.parse(row.get("endsAt").asText()))
                .isEqualTo(Instant.parse("2026-01-15T08:30:00Z").plus(31, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("归属企业留空即个人订阅：enterpriseId 回显为 null")
    void enterpriseIdIsNullWhenNotAssigned() throws Exception {
        createSubscription(memberId, createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].enterpriseId").doesNotExist());
    }

    @Test
    @DisplayName("分配时指定归属企业：enterpriseId 落库并回显")
    void createWithEnterprise() throws Exception {
        Long enterpriseId = createEnterprise("Acme", "acme.com", true, AgentType.CLAUDE);
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("enterpriseId", enterpriseId);
        createSubscription(memberId, body);

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].enterpriseId").value(enterpriseId));
    }

    @Test
    @DisplayName("归属到不存在的企业报 410020")
    void createWithMissingEnterpriseRejected() throws Exception {
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("enterpriseId", 99999L);

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(410020));
    }

    @Test
    @DisplayName("归属到已停用的企业报 410023")
    void createWithDisabledEnterpriseRejected() throws Exception {
        Long enterpriseId = createEnterprise("Acme", "acme.com", false, AgentType.CLAUDE);
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("enterpriseId", enterpriseId);

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(410023));
    }

    @Test
    @DisplayName("套餐的 agent 类型不在企业支持范围内报 410024")
    void createWithAgentTypeOutsideEnterpriseRejected() throws Exception {
        Long enterpriseId = createEnterprise("Codex Only", "codex.example", true, AgentType.CODEX);
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("enterpriseId", enterpriseId);

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(410024));
    }

    @Test
    @DisplayName("更新可改归属企业，也可置空改回个人订阅")
    void updateChangesEnterprise() throws Exception {
        Long enterpriseId = createEnterprise("Acme", "acme.com", true, AgentType.CLAUDE);
        Long subscriptionId = createSubscription(memberId,
                createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));

        Map<String, Object> assign = updateRequest("2026-08-01T00:00:00Z", null, "归到 Acme");
        assign.put("enterpriseId", enterpriseId);
        mockMvc.perform(put("/api/admin/subscriptions/" + subscriptionId)
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(assign)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].enterpriseId").value(enterpriseId));

        Map<String, Object> clear = updateRequest("2026-08-01T00:00:00Z", null, "改回个人");
        clear.put("enterpriseId", null);
        mockMvc.perform(put("/api/admin/subscriptions/" + subscriptionId)
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(clear)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].enterpriseId").doesNotExist());
    }

    @Test
    @DisplayName("账号邮箱落库并回显，统一转小写")
    void createWithAccountEmail() throws Exception {
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("accountEmail", "Zhang@Acme.COM");
        createSubscription(memberId, body);

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].accountEmail").value("zhang@acme.com"));
    }

    @Test
    @DisplayName("归属企业时账号邮箱域名须与企业域名一致，不一致报 410026")
    void createWithAccountEmailOutsideEnterpriseDomainRejected() throws Exception {
        Long enterpriseId = createEnterprise("Acme", "acme.com", true, AgentType.CLAUDE);
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("enterpriseId", enterpriseId);
        body.put("accountEmail", "zhang@other.com");

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(410026));
    }

    @Test
    @DisplayName("归属企业且账号邮箱域名一致时放行，大小写不计")
    void createWithAccountEmailMatchingEnterpriseDomain() throws Exception {
        Long enterpriseId = createEnterprise("Acme", "acme.com", true, AgentType.CLAUDE);
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("enterpriseId", enterpriseId);
        body.put("accountEmail", "Zhang@ACME.com");

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("个人订阅不校验账号邮箱域名：任意域名都放行")
    void personalSubscriptionSkipsDomainCheck() throws Exception {
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("accountEmail", "zhang@other.com");

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("账号邮箱格式不合法报 110001")
    void createWithMalformedAccountEmailFailsValidation() throws Exception {
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("accountEmail", "zhangsan");

        mockMvc.perform(post("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("账号邮箱留空即未录：回显为 null")
    void accountEmailIsNullWhenBlank() throws Exception {
        Map<String, Object> body = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        body.put("accountEmail", "  ");
        createSubscription(memberId, body);

        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].accountEmail").doesNotExist());
    }

    @Test
    @DisplayName("更新可改账号邮箱，也可留空清除（与凭据的沿用语义不同）")
    void updateChangesAndClearsAccountEmail() throws Exception {
        Map<String, Object> created = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        created.put("accountEmail", "zhang@acme.com");
        Long subscriptionId = createSubscription(memberId, created);

        Map<String, Object> change = updateRequest("2026-08-01T00:00:00Z", null, "换账号");
        change.put("accountEmail", "li@acme.com");
        mockMvc.perform(put("/api/admin/subscriptions/" + subscriptionId)
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(change)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].accountEmail").value("li@acme.com"));

        Map<String, Object> clear = updateRequest("2026-08-01T00:00:00Z", null, "清掉账号");
        clear.put("accountEmail", null);
        mockMvc.perform(put("/api/admin/subscriptions/" + subscriptionId)
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(clear)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users/" + memberId + "/subscriptions")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].accountEmail").doesNotExist());
    }

    @Test
    @DisplayName("更新时把订阅归到企业，账号邮箱域名不一致同样报 410026")
    void updateWithAccountEmailOutsideEnterpriseDomainRejected() throws Exception {
        Long enterpriseId = createEnterprise("Acme", "acme.com", true, AgentType.CLAUDE);
        Map<String, Object> created = createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null);
        created.put("accountEmail", "zhang@other.com");
        Long subscriptionId = createSubscription(memberId, created);

        Map<String, Object> body = updateRequest("2026-08-01T00:00:00Z", null, "");
        body.put("enterpriseId", enterpriseId);
        body.put("accountEmail", "zhang@other.com");
        mockMvc.perform(put("/api/admin/subscriptions/" + subscriptionId)
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(410026));
    }

    @Test
    @DisplayName("更新归属时同样校验企业支持的 agent 类型，不匹配报 410024")
    void updateWithAgentTypeOutsideEnterpriseRejected() throws Exception {
        Long enterpriseId = createEnterprise("Codex Only", "codex.example", true, AgentType.CODEX);
        Long subscriptionId = createSubscription(memberId,
                createRequest(monthlyPlanId, "2026-08-01T00:00:00Z", null));

        Map<String, Object> body = updateRequest("2026-08-01T00:00:00Z", null, "");
        body.put("enterpriseId", enterpriseId);
        mockMvc.perform(put("/api/admin/subscriptions/" + subscriptionId)
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(410024));
    }
}
