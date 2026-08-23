package ai.mintpop.lane.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.NodeGroupRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminNodeGroupControllerTest extends MysqlTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProxyNodeRepository nodeRepository;

    @Autowired
    private NodeGroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    @MockitoBean
    private SubFetchClient subFetchClient;

    private DatabaseFixtures fixtures;
    private Long adminId;

    private static final String SUB_URL = "https://sub.example.com/c?token=秘密token";

    private String bearer(Long userId) {
        return "Bearer " + sessionTokenService.issue(userId, Duration.ofMinutes(10));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String sampleSubscription() {
        try (InputStream in = getClass().getResourceAsStream("/sub/sample.yaml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        adminId = fixtures.createUser("logto-admin", ADMIN, ACTIVE, null, null);
        when(subFetchClient.fetch(anyString())).thenReturn(sampleSubscription());
    }

    /** 建分组并勾选导入两个真节点，返回分组 id */
    private Long createGroupImportingTwoNodes() throws Exception {
        var body = Map.of("name", "机场A", "subUrl", SUB_URL,
                "selectedNames", List.of("香港 IEPL-01", "[境外用户专用]GPT01"));
        var result = mockMvc.perform(post("/api/admin/node-groups").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    @Test
    @DisplayName("preview 返回全部解析条目并标记疑似信息条目，不落库、不回传敏感参数")
    void previewSubscription() throws Exception {
        mockMvc.perform(post("/api/admin/node-groups/preview").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("subUrl", SUB_URL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].sourceName").value("剩余流量：121.54 GB"))
                .andExpect(jsonPath("$.data[0].suspectedInfo").value(true))
                .andExpect(jsonPath("$.data[2].sourceName").value("香港 IEPL-01"))
                .andExpect(jsonPath("$.data[2].sourceType").value("anytls"))
                .andExpect(jsonPath("$.data[2].suspectedInfo").value(false))
                .andExpect(jsonPath("$.data[2].existed").value(false))
                // 敏感参数一个字符都不回传
                .andExpect(jsonPath("$.data[2].params").doesNotExist());

        assertThat(nodeRepository.findAll(null)).isEmpty();
        assertThat(groupRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("创建分组：按勾选导入为 FRONT+MIHOMO 节点，整份参数加密，链接与来源字段落库")
    void createGroupAndImport() throws Exception {
        Long groupId = createGroupImportingTwoNodes();

        // 只导入勾选的 2 个，信息条目没进来
        List<ProxyNodeDto> nodes = nodeRepository.findByGroupId(groupId);
        assertThat(nodes).hasSize(2);
        ProxyNodeDto hk = nodes.get(0);
        assertThat(hk.getName()).isEqualTo("香港 IEPL-01");
        assertThat(hk.getRole()).isEqualTo(NodeRole.FRONT);
        assertThat(hk.getProtocol()).isEqualTo(NodeProtocol.MIHOMO);
        assertThat(hk.getServerAddr()).isEqualTo("hk02a.example.com");
        assertThat(hk.getPort()).isEqualTo(35356);
        assertThat(hk.getSourceName()).isEqualTo("香港 IEPL-01");
        assertThat(hk.getSourceType()).isEqualTo("anytls");
        assertThat(hk.getSecret()).containsEntry("password", "uuid-秘密-1").containsEntry("type", "anytls");
        assertThat(hk.getExtraConfig()).isEmpty();

        // 分组列表：数量、打码链接（token 不出现）
        mockMvc.perform(get("/api/admin/node-groups").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].name").value("机场A"))
                .andExpect(jsonPath("$.data[0].nodeCount").value(2))
                .andExpect(jsonPath("$.data[0].subUrlMasked").value("https://sub.example.com/…"));

        // 节点列表带分组信息与真实 type
        mockMvc.perform(get("/api/admin/nodes").param("role", "FRONT").header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.data[0].groupId").value(groupId))
                .andExpect(jsonPath("$.data[0].groupName").value("机场A"))
                .andExpect(jsonPath("$.data[0].sourceType").value("anytls"));
    }

    @Test
    @DisplayName("导入撞上已有的全局节点名时自动加后缀")
    void nameCollisionGetsSuffix() throws Exception {
        fixtures.createFrontNode("香港 IEPL-01");
        Long groupId = createGroupImportingTwoNodes();

        assertThat(nodeRepository.findByGroupId(groupId))
                .extracting(ProxyNodeDto::getName)
                .contains("香港 IEPL-01 (2)");
    }

    @Test
    @DisplayName("分组重名报 410010；勾选了订阅里不存在的节点名报 410014 且整组不落库")
    void createGroupFailureModes() throws Exception {
        createGroupImportingTwoNodes();
        var duplicateName = Map.of("name", "机场A", "subUrl", SUB_URL, "selectedNames", List.of("香港 IEPL-01"));
        mockMvc.perform(post("/api/admin/node-groups").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(duplicateName)))
                .andExpect(jsonPath("$.code").value(410010));

        var ghostSelection = Map.of("name", "机场B", "subUrl", SUB_URL, "selectedNames", List.of("订阅里没有的名字"));
        mockMvc.perform(post("/api/admin/node-groups").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(ghostSelection)))
                .andExpect(jsonPath("$.code").value(410014));
        assertThat(groupRepository.existsByName("机场B")).isFalse();
    }

    @Test
    @DisplayName("refresh-preview 标出已入池节点；import 对已存在的更新参数、新勾选的入库")
    void refreshAndIncrementalImport() throws Exception {
        Long groupId = createGroupImportingTwoNodes();

        // 第二次拉取订阅内容有变化：香港节点换了端口，多了个新节点
        String updatedYaml = sampleSubscription().replace("port: 35356", "port: 40000")
                + "\n"; // 保持 YAML 合法
        updatedYaml = updatedYaml.replace("proxy-groups:",
                "    - { name: '新加坡-01', type: anytls, server: sg01.example.com, port: 35357, password: uuid-秘密-1 }\nproxy-groups:");
        when(subFetchClient.fetch(anyString())).thenReturn(updatedYaml);

        mockMvc.perform(post("/api/admin/node-groups/" + groupId + "/refresh-preview")
                        .header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[2].sourceName").value("香港 IEPL-01"))
                .andExpect(jsonPath("$.data[2].existed").value(true))
                // 索引 3 是订阅里原有的 GPT01（也在「建组导入两节点」里被勾选导入过，故 existed=true）；
                // 新增的「新加坡-01」被追加在 proxies 列表末尾（坏条目之后），落在索引 4
                .andExpect(jsonPath("$.data[3].sourceName").value("[境外用户专用]GPT01"))
                .andExpect(jsonPath("$.data[3].existed").value(true))
                .andExpect(jsonPath("$.data[4].sourceName").value("新加坡-01"))
                .andExpect(jsonPath("$.data[4].existed").value(false));

        var body = Map.of("selectedNames", List.of("香港 IEPL-01", "新加坡-01"));
        mockMvc.perform(post("/api/admin/node-groups/" + groupId + "/import")
                        .header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0));

        List<ProxyNodeDto> nodes = nodeRepository.findByGroupId(groupId);
        assertThat(nodes).hasSize(3);
        // 已存在的节点原地更新端口，名字保持库里的（没有产生「香港 IEPL-01 (2)」）
        ProxyNodeDto hk = nodeRepository.findByGroupIdAndSourceName(groupId, "香港 IEPL-01").orElseThrow();
        assertThat(hk.getPort()).isEqualTo(40000);
        assertThat(hk.getSecret()).containsEntry("port", 40000);
        assertThat(nodeRepository.findByGroupIdAndSourceName(groupId, "新加坡-01")).isPresent();
    }

    @Test
    @DisplayName("改名生效且重名报 410010；分组不存在报 410009")
    void renameGroup() throws Exception {
        Long groupId = createGroupImportingTwoNodes();
        mockMvc.perform(put("/api/admin/node-groups/" + groupId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "机场A-新名"))))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(groupRepository.findById(groupId).orElseThrow().getName()).isEqualTo("机场A-新名");

        mockMvc.perform(put("/api/admin/node-groups/99999").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "X"))))
                .andExpect(jsonPath("$.code").value(410009));
    }

    @Test
    @DisplayName("只改大小写的分组改名不被表的 ci 排序规则误判为重名")
    void renameGroupCaseOnlyChangeSucceeds() throws Exception {
        var body = Map.of("name", "Airport A", "subUrl", SUB_URL, "selectedNames", List.of("香港 IEPL-01"));
        var result = mockMvc.perform(post("/api/admin/node-groups").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long groupId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();

        mockMvc.perform(put("/api/admin/node-groups/" + groupId).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "AIRPORT A"))))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(groupRepository.findById(groupId).orElseThrow().getName()).isEqualTo("AIRPORT A");
    }

    @Test
    @DisplayName("改名撞上另一个已存在的分组名时报 410010，且该分组名字不变")
    void renameToExistingGroupNameFails() throws Exception {
        Long groupA = createGroupImportingTwoNodes();
        var bodyB = Map.of("name", "机场B", "subUrl", SUB_URL, "selectedNames", List.of("香港 IEPL-01"));
        var resultB = mockMvc.perform(post("/api/admin/node-groups").header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(bodyB)))
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Long groupB = objectMapper.readTree(resultB.getResponse().getContentAsString()).get("data").asLong();

        mockMvc.perform(put("/api/admin/node-groups/" + groupB).header("Authorization", bearer(adminId))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "机场A"))))
                .andExpect(jsonPath("$.code").value(410010));
        assertThat(groupRepository.findById(groupB).orElseThrow().getName()).isEqualTo("机场B");
        assertThat(groupRepository.findById(groupA).orElseThrow().getName()).isEqualTo("机场A");
    }

    @Test
    @DisplayName("删除分组连带删除组内节点；组内有节点被用户绑定时报 410013 且一个都不删")
    void deleteGroup() throws Exception {
        Long groupId = createGroupImportingTwoNodes();
        Long nodeId = nodeRepository.findByGroupId(groupId).get(0).getId();
        fixtures.createUser("logto-user-1", nodeId, null);

        mockMvc.perform(delete("/api/admin/node-groups/" + groupId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(410013));
        assertThat(nodeRepository.findByGroupId(groupId)).hasSize(2);

        // 解绑后可整组删除
        jdbc.update("UPDATE app_user SET front_node_id = NULL WHERE front_node_id = ?", nodeId);
        mockMvc.perform(delete("/api/admin/node-groups/" + groupId).header("Authorization", bearer(adminId)))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(nodeRepository.findByGroupId(groupId)).isEmpty();
        assertThat(groupRepository.findById(groupId)).isEmpty();
    }
}
