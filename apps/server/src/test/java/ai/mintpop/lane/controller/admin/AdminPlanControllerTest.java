package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.HashMap;
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
class AdminPlanControllerTest extends MysqlTestBase {

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
    private PlanRepository planRepository;

    private Long adminId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** 一份合法的套餐入参，调用方按需覆盖个别字段 */
    private Map<String, Object> validBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "月付套餐");
        body.put("durationDays", 30);
        body.put("price", 29.9);
        body.put("currency", "USD");
        body.put("enabled", true);
        body.put("remark", "首发款");
        return body;
    }

    private Long createPlan(Map<String, Object> body) throws Exception {
        var result = mockMvc.perform(post("/api/admin/plans").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
    }

    @Test
    @DisplayName("创建套餐后列表回显全部字段，多个套餐按 id 升序")
    void createAndList() throws Exception {
        Long firstId = createPlan(validBody());
        Map<String, Object> second = validBody();
        second.put("name", "季付套餐");
        second.put("durationDays", 90);
        second.put("price", 79.0);
        second.put("currency", "CNY");
        second.put("remark", "");
        createPlan(second);

        mockMvc.perform(get("/api/admin/plans").header("Authorization", bearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].name").value("月付套餐"))
                .andExpect(jsonPath("$.data[0].durationDays").value(30))
                .andExpect(jsonPath("$.data[0].price").value(29.9))
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].remark").value("首发款"))
                .andExpect(jsonPath("$.data[0].createdAt").exists())
                .andExpect(jsonPath("$.data[0].updatedAt").exists())
                .andExpect(jsonPath("$.data[1].name").value("季付套餐"))
                .andExpect(jsonPath("$.data[1].durationDays").value(90))
                .andExpect(jsonPath("$.data[1].currency").value("CNY"));
    }

    @Test
    @DisplayName("套餐重名报 410018 且不落库")
    void duplicateNameRejected() throws Exception {
        createPlan(validBody());

        mockMvc.perform(post("/api/admin/plans").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(jsonPath("$.code").value(410018));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM plan", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("参数校验：天数至少 1、价格不能为负、名称必填，均报 110001")
    void validationFailures() throws Exception {
        Map<String, Object> zeroDays = validBody();
        zeroDays.put("durationDays", 0);
        mockMvc.perform(post("/api/admin/plans").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(zeroDays)))
                .andExpect(jsonPath("$.code").value(110001));

        Map<String, Object> negativePrice = validBody();
        negativePrice.put("price", -1);
        mockMvc.perform(post("/api/admin/plans").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(negativePrice)))
                .andExpect(jsonPath("$.code").value(110001));

        Map<String, Object> blankName = validBody();
        blankName.put("name", " ");
        mockMvc.perform(post("/api/admin/plans").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(blankName)))
                .andExpect(jsonPath("$.code").value(110001));
    }

    @Test
    @DisplayName("更新覆盖全部字段（含停用）；套餐不存在报 410017；改名撞已有套餐报 410018")
    void updatePlan() throws Exception {
        Long planId = createPlan(validBody());
        Map<String, Object> other = validBody();
        other.put("name", "季付套餐");
        createPlan(other);

        Map<String, Object> updated = validBody();
        updated.put("name", "月付套餐·新");
        updated.put("durationDays", 31);
        updated.put("price", 39.9);
        updated.put("currency", "CNY");
        updated.put("enabled", false);
        updated.put("remark", "调价后");
        mockMvc.perform(put("/api/admin/plans/" + planId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(updated)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/plans").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("月付套餐·新"))
                .andExpect(jsonPath("$.data[0].durationDays").value(31))
                .andExpect(jsonPath("$.data[0].price").value(39.9))
                .andExpect(jsonPath("$.data[0].currency").value("CNY"))
                .andExpect(jsonPath("$.data[0].enabled").value(false))
                .andExpect(jsonPath("$.data[0].remark").value("调价后"));

        mockMvc.perform(put("/api/admin/plans/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(jsonPath("$.code").value(410017));

        Map<String, Object> collision = validBody();
        collision.put("name", "季付套餐");
        mockMvc.perform(put("/api/admin/plans/" + planId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(collision)))
                .andExpect(jsonPath("$.code").value(410018));
    }

    @Test
    @DisplayName("重名预检查与插入之间的竞态由唯一索引兜底，映射为 410018 而非内部错误")
    void duplicateNameRaceFallsBackToUniqueIndex() throws Exception {
        createPlan(validBody());

        // 模拟两个管理员同时提交、双双通过预检查的间隙：把预检查打成「不存在」，
        // 让插入直接撞数据库唯一索引
        doReturn(false).when(planRepository).existsByName(anyString());
        mockMvc.perform(post("/api/admin/plans").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(jsonPath("$.code").value(410018));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM plan", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("只改大小写的改名不被表的 ci 排序规则误判为重名")
    void renameCaseOnlyChangeSucceeds() throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", "Plan A");
        Long planId = createPlan(body);

        Map<String, Object> renamed = validBody();
        renamed.put("name", "PLAN A");
        mockMvc.perform(put("/api/admin/plans/" + planId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(renamed)))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/plans").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("PLAN A"));
    }

    @Test
    @DisplayName("删除套餐后列表不再出现；删除不存在的套餐报 410017")
    void deletePlan() throws Exception {
        Long planId = createPlan(validBody());

        mockMvc.perform(delete("/api/admin/plans/" + planId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/plans").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(delete("/api/admin/plans/" + planId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410017));
    }
}
