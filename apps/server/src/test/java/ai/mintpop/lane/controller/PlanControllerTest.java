package ai.mintpop.lane.controller;

import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlanControllerTest extends MysqlTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProxyNodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private SessionTokenService sessionTokenService;

    private Long memberId;

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private void plan(String name, AgentType agentType, int days, String price, boolean enabled, String remark) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setAgentType(agentType);
        plan.setDurationDays(days);
        plan.setPrice(new BigDecimal(price));
        plan.setCurrency(Currency.USD);
        plan.setEnabled(enabled);
        plan.setRemark(remark);
        planRepository.create(plan);
    }

    @BeforeEach
    void setUp() {
        DatabaseFixtures fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        memberId = fixtures.createUser("logto-member", null, null);
        plan("Codex 季付", AgentType.CODEX, 90, "199.00", true, "内部备注-codex");
        plan("Claude 年付", AgentType.CLAUDE, 365, "999.00", true, "内部备注-年");
        plan("Claude 月付", AgentType.CLAUDE, 30, "99.99", true, "内部备注-月");
        plan("已下架", AgentType.CLAUDE, 30, "1.00", false, "内部备注-下架");
    }

    @Test
    @DisplayName("普通成员能看到上架套餐，按 agent 类型再按时长排序，不含下架套餐与内部备注")
    void listsEnabledPlansWithoutRemark() throws Exception {
        mockMvc.perform(get("/api/plans").header("Authorization", bearer(memberId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].name").value("Claude 月付"))
                .andExpect(jsonPath("$.data[1].name").value("Claude 年付"))
                .andExpect(jsonPath("$.data[2].name").value("Codex 季付"))
                .andExpect(jsonPath("$.data[0].price").value(99.99))
                .andExpect(jsonPath("$.data[0].currency").value("USD"))
                .andExpect(content().string(not(containsString("内部备注"))))
                .andExpect(content().string(not(containsString("已下架"))));
    }

    @Test
    @DisplayName("未登录得 401")
    void anonymousGets401() throws Exception {
        mockMvc.perform(get("/api/plans")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("用户侧套餐列表带上描述与图片，管理员备注仍然不出现")
    void userListExposesMedia() throws Exception {
        // 只留这一个套餐，断言就不必依赖 setUp 里那几个的排序
        jdbc.update("DELETE FROM plan");
        Plan withMedia = new Plan();
        withMedia.setName("Claude 双月付");
        withMedia.setAgentType(AgentType.CLAUDE);
        withMedia.setDurationDays(60);
        withMedia.setPrice(new BigDecimal("159.00"));
        withMedia.setCurrency(Currency.USD);
        withMedia.setEnabled(true);
        withMedia.setRemark("内部备注-双月");
        withMedia.setDescription("含 5 个并发席位，不限流量");
        withMedia.setImageUrl("https://assets.lane.mintpop.ai/plans/2026/09/a.png");
        planRepository.create(withMedia);

        mockMvc.perform(get("/api/plans").header("Authorization", bearer(memberId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].description").value("含 5 个并发席位，不限流量"))
                .andExpect(jsonPath("$.data[0].imageUrl")
                        .value("https://assets.lane.mintpop.ai/plans/2026/09/a.png"))
                .andExpect(content().string(not(containsString("内部备注"))));
    }
}
