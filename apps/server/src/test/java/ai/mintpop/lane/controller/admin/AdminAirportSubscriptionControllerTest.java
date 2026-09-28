package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.AirportRepository;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static ai.mintpop.lane.enumeration.UserRole.ADMIN;
import static ai.mintpop.lane.enumeration.UserStatus.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@AutoConfigureMockMvc
class AdminAirportSubscriptionControllerTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private AirportSubscriptionRepository airportSubscriptionRepository;

    @Autowired
    private AirportRepository airportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFrontNodeRepository userFrontNodeRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    @MockitoBean
    private SubFetchClient subFetchClient;

    @MockitoBean
    private FailureDomainResolver failureDomainResolver;

    private DatabaseFixtures fixtures;
    private Long adminId;
    private Long airportId;

    private static final String SUB_URL = "https://sub.example.com/c?token=秘密token";

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /**
     * 现役机场形态的订阅：2 条信息条目、2 个美国节点、1 个港节点、1 个名字不带国别的节点。
     * 导入只取两个美国节点，其余全部略过。
     */
    private static final String SUBSCRIPTION = """
            proxies:
                - { name: '剩余流量：50.3 GB', type: anytls, server: hk01a.example.com, port: 35355, password: uuid-秘密-1 }
                - { name: '套餐到期：2027-05-02', type: anytls, server: hk01a.example.com, port: 35355, password: uuid-秘密-1 }
                - { name: '🇭🇰[HK]HongKong01', type: anytls, server: hk01a.example.com, port: 35355, password: uuid-秘密-1 }
                - { name: '🇺🇸[US]Santa Clara 01', type: anytls, server: us01a.example.com, port: 35660, password: uuid-秘密-1, udp: true }
                - { name: '🇺🇸[US]San Jose07', type: anytls, server: us07a.example.com, port: 35668, password: uuid-秘密-1 }
                - { name: '[境外用户专用]GPT01', type: anytls, server: hw01v.example.com, port: 19279, password: uuid-秘密-1 }
            """;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository,
                airportRepository, airportSubscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
        airportId = fixtures.createAirport("泰山云");
        stubSubscription(SUBSCRIPTION);
    }

    private void stubSubscription(String yaml) {
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(yaml, null, null, null, null));
    }

    /** 只给订阅名与链接建订阅，返回订阅 id */
    private Long createGroup(String name) throws Exception {
        var body = Map.of("name", name, "subUrl", SUB_URL, "airportId", airportId, "account", "a@x.com", "bandwidthMbps", 300);
        var result = mockMvc.perform(post("/api/admin/airport-subscriptions").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    /** 建订阅「机场A」，自动导入订阅里的两个美国节点 */
    private Long createGroupImportingTwoNodes() throws Exception {
        return createGroup("机场A");
    }

    @Test
    @DisplayName("创建订阅：只给名字与链接，自动导入全部美国节点为 FRONT+MIHOMO，信息条目与非美国节点一律不进")
    void createGroupImportsUsNodesOnly() throws Exception {
        Long airportSubscriptionId = createGroupImportingTwoNodes();

        List<ProxyNodeDto> nodes = nodeRepository.findByAirportSubscriptionId(airportSubscriptionId);
        assertThat(nodes).extracting(ProxyNodeDto::getSourceName)
                .containsExactly("🇺🇸[US]Santa Clara 01", "🇺🇸[US]San Jose07");
        ProxyNodeDto us = nodes.get(0);
        assertThat(us.getName()).isEqualTo("🇺🇸[US]Santa Clara 01");
        assertThat(us.getRole()).isEqualTo(NodeRole.FRONT);
        assertThat(us.getProtocol()).isEqualTo(NodeProtocol.MIHOMO);
        assertThat(us.getServerAddr()).isEqualTo("us01a.example.com");
        assertThat(us.getPort()).isEqualTo(35660);
        assertThat(us.getSourceType()).isEqualTo("anytls");
        assertThat(us.getSecret()).containsEntry("password", "uuid-秘密-1").containsEntry("type", "anytls");
        assertThat(us.getExtraConfig()).isEmpty();

        // 订阅列表：数量、打码链接（token 不出现）
        mockMvc.perform(get("/api/admin/airport-subscriptions").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("机场A"))
                .andExpect(jsonPath("$.data[0].nodeCount").value(2))
                .andExpect(jsonPath("$.data[0].subUrlMasked").value("https://sub.example.com/…"));

        // 节点列表带订阅信息与真实 type
        mockMvc.perform(get("/api/admin/nodes").param("role", "FRONT").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].airportSubscriptionId").value(airportSubscriptionId))
                .andExpect(jsonPath("$.data[0].airportSubscriptionName").value("机场A"))
                .andExpect(jsonPath("$.data[0].sourceType").value("anytls"));
    }

    @Test
    @DisplayName("导入撞上已有的全局节点名时自动加后缀")
    void nameCollisionGetsSuffix() throws Exception {
        fixtures.createFrontNode("🇺🇸[US]San Jose07");
        Long airportSubscriptionId = createGroupImportingTwoNodes();

        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId))
                .extracting(ProxyNodeDto::getName)
                .contains("🇺🇸[US]San Jose07 (2)");
    }

    @Test
    @DisplayName("订阅重名报 410010；订阅里一个美国节点都没有报 410049 且不建空订阅")
    void createGroupFailureModes() throws Exception {
        createGroupImportingTwoNodes();
        mockMvc.perform(post("/api/admin/airport-subscriptions").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "机场A", "subUrl", SUB_URL, "airportId", airportId,
                                "account", "a@x.com", "bandwidthMbps", 300))))
                .andExpect(jsonPath("$.code").value(410010));

        stubSubscription("""
                proxies:
                  - { name: '剩余流量：50.3 GB', type: anytls, server: hk01a.example.com, port: 35355, password: p }
                  - { name: '🇭🇰[HK]HongKong01', type: anytls, server: hk01a.example.com, port: 35355, password: p }
                  - { name: 'United States 03', type: anytls, server: us03a.example.com, port: 35663, password: p }
                """);
        mockMvc.perform(post("/api/admin/airport-subscriptions").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "机场B", "subUrl", SUB_URL, "airportId", airportId,
                                "account", "a@x.com", "bandwidthMbps", 300))))
                .andExpect(jsonPath("$.code").value(410049));
        assertThat(airportSubscriptionRepository.existsByName("机场B")).isFalse();
    }

    @Test
    @DisplayName("重新拉取并导入：已存在的美国节点原地更新参数，新出现的美国节点入库，非美国节点仍不进")
    void reimportUpdatesExistingAndAddsNewUsNodes() throws Exception {
        Long airportSubscriptionId = createGroupImportingTwoNodes();

        // 第二次拉取订阅内容有变化：Santa Clara 01 换了端口，多了一个美国节点和一个港节点
        stubSubscription(SUBSCRIPTION.replace("port: 35660", "port: 40000") + """
                    - { name: '🇺🇸[US]San Francisco09', type: anytls, server: us09a.example.com, port: 35675, password: uuid-秘密-1 }
                    - { name: '🇭🇰[HK]HongKong02', type: anytls, server: hk02a.example.com, port: 35356, password: uuid-秘密-1 }
                """);

        mockMvc.perform(post("/api/admin/airport-subscriptions/" + airportSubscriptionId + "/import")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));

        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)).extracting(ProxyNodeDto::getSourceName)
                .containsExactlyInAnyOrder("🇺🇸[US]Santa Clara 01", "🇺🇸[US]San Jose07", "🇺🇸[US]San Francisco09");
        // 已存在的节点原地更新端口，名字保持库里的（没有产生「… (2)」）
        ProxyNodeDto updated = nodeRepository.findByAirportSubscriptionIdAndSourceName(airportSubscriptionId, "🇺🇸[US]Santa Clara 01")
                .orElseThrow();
        assertThat(updated.getName()).isEqualTo("🇺🇸[US]Santa Clara 01");
        assertThat(updated.getPort()).isEqualTo(40000);
        assertThat(updated.getSecret()).containsEntry("port", 40000);
    }

    @Test
    @DisplayName("改名生效且重名报 410010；订阅不存在报 410009")
    void renameGroup() throws Exception {
        Long airportSubscriptionId = createGroupImportingTwoNodes();
        mockMvc.perform(put("/api/admin/airport-subscriptions/" + airportSubscriptionId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "机场A-新名", "account", "a@x.com"))))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(airportSubscriptionRepository.findById(airportSubscriptionId).orElseThrow().getName()).isEqualTo("机场A-新名");

        mockMvc.perform(put("/api/admin/airport-subscriptions/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "X", "account", "a@x.com"))))
                .andExpect(jsonPath("$.code").value(410009));
    }

    @Test
    @DisplayName("只改大小写的订阅改名不被表的 ci 排序规则误判为重名")
    void renameGroupCaseOnlyChangeSucceeds() throws Exception {
        Long airportSubscriptionId = createGroup("Airport A");

        mockMvc.perform(put("/api/admin/airport-subscriptions/" + airportSubscriptionId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "AIRPORT A", "account", "a@x.com"))))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(airportSubscriptionRepository.findById(airportSubscriptionId).orElseThrow().getName()).isEqualTo("AIRPORT A");
    }

    @Test
    @DisplayName("改名撞上另一个已存在的订阅名时报 410010，且该订阅名字不变")
    void renameToExistingGroupNameFails() throws Exception {
        Long groupA = createGroupImportingTwoNodes();
        Long groupB = createGroup("机场B");

        mockMvc.perform(put("/api/admin/airport-subscriptions/" + groupB).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "机场A", "account", "a@x.com"))))
                .andExpect(jsonPath("$.code").value(410010));
        assertThat(airportSubscriptionRepository.findById(groupB).orElseThrow().getName()).isEqualTo("机场B");
        assertThat(airportSubscriptionRepository.findById(groupA).orElseThrow().getName()).isEqualTo("机场A");
    }

    @Test
    @DisplayName("删除订阅连带删除订阅内节点；订阅内有节点被用户绑定时报 410013 且一个都不删")
    void deleteGroup() throws Exception {
        Long airportSubscriptionId = createGroupImportingTwoNodes();
        Long nodeId = nodeRepository.findByAirportSubscriptionId(airportSubscriptionId).get(0).getId();
        fixtures.createUser("logto-user-1", nodeId, null);

        mockMvc.perform(delete("/api/admin/airport-subscriptions/" + airportSubscriptionId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410013));
        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)).hasSize(2);

        // 解绑后可整组删除
        jdbc.update("UPDATE app_user SET front_node_id = NULL WHERE front_node_id = ?", nodeId);
        mockMvc.perform(delete("/api/admin/airport-subscriptions/" + airportSubscriptionId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)).isEmpty();
        assertThat(airportSubscriptionRepository.findById(airportSubscriptionId)).isEmpty();
    }

    @Test
    @DisplayName("订阅内节点是某人前置集合里的非主成员（不在任何人的 front_node_id 上）时，删订阅同样报 410013 而不是数据库异常")
    void deleteGroupBlockedByNonPrimaryFrontMembership() throws Exception {
        Long airportSubscriptionId = createGroupImportingTwoNodes();
        List<ProxyNodeDto> nodes = nodeRepository.findByAirportSubscriptionId(airportSubscriptionId);
        Long primaryNodeId = nodes.get(0).getId();
        Long secondaryNodeId = nodes.get(1).getId();
        // 该用户的「主」前置节点是 primaryNodeId，secondaryNodeId 只是它前置集合里的非主成员——
        // existsByFrontNodeId 查不到 secondaryNodeId，必须靠 user_front_node 的引用检查才能挡住
        Long userId = fixtures.createUser("logto-user-1", primaryNodeId, null);
        userFrontNodeRepository.replaceForUser(userId, List.of(primaryNodeId, secondaryNodeId));

        mockMvc.perform(delete("/api/admin/airport-subscriptions/" + airportSubscriptionId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410013));
        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)).hasSize(2);

        // 解绑后可整组删除。断言要落到库上：只看 code=0 的话，删除若是空操作也发现不了
        userFrontNodeRepository.deleteByUserId(userId);
        jdbc.update("UPDATE app_user SET front_node_id = NULL WHERE id = ?", userId);
        mockMvc.perform(delete("/api/admin/airport-subscriptions/" + airportSubscriptionId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(nodeRepository.findByAirportSubscriptionId(airportSubscriptionId)).isEmpty();
        assertThat(airportSubscriptionRepository.findById(airportSubscriptionId)).isEmpty();
    }

    @Test
    @DisplayName("新建订阅：机场不存在报 410051；列表带机场名、账号、带宽与主用容量")
    void createCarriesAirportAccountBandwidth() throws Exception {
        mockMvc.perform(post("/api/admin/airport-subscriptions").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "X", "subUrl", SUB_URL, "airportId", 99999,
                                "account", "a@x.com", "bandwidthMbps", 300))))
                .andExpect(jsonPath("$.code").value(410051));

        createGroupImportingTwoNodes();
        mockMvc.perform(get("/api/admin/airport-subscriptions").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].airportName").value("泰山云"))
                .andExpect(jsonPath("$.data[0].account").value("a@x.com"))
                .andExpect(jsonPath("$.data[0].bandwidthMbps").value(300))
                .andExpect(jsonPath("$.data[0].primaryCapacity").value(15));
    }

    @Test
    @DisplayName("编辑只改名称、账号、备注：入参里带机场与带宽也不生效")
    void updateIgnoresAirportAndBandwidth() throws Exception {
        Long id = createGroupImportingTwoNodes();
        Long otherAirport = fixtures.createAirport("B 机场");
        mockMvc.perform(put("/api/admin/airport-subscriptions/" + id).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "新名", "account", "b@x.com", "remark", "r",
                                "airportId", otherAirport, "bandwidthMbps", 999))))
                .andExpect(jsonPath("$.code").value(0));
        AirportSubscriptionDto saved = airportSubscriptionRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("新名");
        assertThat(saved.getAccount()).isEqualTo("b@x.com");
        assertThat(saved.getAirportId()).isEqualTo(airportId);
        assertThat(saved.getBandwidthMbps()).isEqualTo(300);
    }
}
