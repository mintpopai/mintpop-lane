package ai.mintpop.lane.support;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 测试数据夹具。造数据一律走真实 repository，好让加密路径也被覆盖到；
 * 只有清库这种绕过业务语义的操作才直接用 JdbcTemplate。
 */
public class DatabaseFixtures {

    private final JdbcTemplate jdbc;
    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    public DatabaseFixtures(JdbcTemplate jdbc, ProxyNodeRepository nodeRepository,
                             UserRepository userRepository, SubscriptionRepository subscriptionRepository) {
        this.jdbc = jdbc;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 清空全部业务表。外键约束在清库期间临时关掉，顺序因此不敏感。 */
    public void clearAll() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("TRUNCATE TABLE subscription");
        jdbc.execute("TRUNCATE TABLE app_user");
        jdbc.execute("TRUNCATE TABLE proxy_node");
        jdbc.execute("TRUNCATE TABLE node_group");
        jdbc.execute("TRUNCATE TABLE plan");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 建一个 trojan 协议的第一跳节点 */
    public Long createFrontNode(String name) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName(name);
        node.setRole(NodeRole.FRONT);
        node.setProtocol(NodeProtocol.TROJAN);
        node.setServerAddr("us.example.com");
        node.setPort(443);
        node.setExtraConfig(Map.of("sni", "us.example.com"));
        node.setSecret(Map.of("password", "front-密码"));
        return nodeRepository.create(node);
    }

    /** 建一个 socks5 协议的落地节点，出口 IP 由调用方指定，容量走数据库默认值 10 */
    public Long createLandNode(String name, String egressIp) {
        return createLandNode(name, egressIp, null);
    }

    /** 建一个 socks5 协议的落地节点；capacity 传 null 表示走数据库默认值 10 */
    public Long createLandNode(String name, String egressIp, Integer capacity) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName(name);
        node.setRole(NodeRole.LAND);
        node.setProtocol(NodeProtocol.SOCKS5);
        node.setServerAddr(egressIp);
        node.setPort(50101);
        node.setSecret(Map.of("username", "land-用户名", "password", "land-密码"));
        node.setEgressIp(egressIp);
        node.setCapacity(capacity);
        return nodeRepository.create(node);
    }

    /** 直接读原始列，用于断言库里存的确实是密文 */
    public String readRawCipherColumn(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id);
    }

    /** 建一个普通用户（无订阅、无凭据） */
    public Long createUser(String subject, Long frontNodeId, Long landNodeId) {
        return createUser(subject, UserRole.MEMBER, UserStatus.ACTIVE, frontNodeId, landNodeId);
    }

    public Long createUser(String subject, UserRole role, UserStatus status, Long frontNodeId, Long landNodeId) {
        UserDto user = new UserDto();
        user.setSubject(subject);
        user.setEmail(subject + "@test.example");
        user.setRole(role);
        user.setStatus(status);
        user.setFrontNodeId(frontNodeId);
        user.setLandNodeId(landNodeId);
        return userRepository.create(user);
    }

    /**
     * 给用户建一条订阅；credential 传 null 表示未录入凭据。
     * 套餐相关字段填固定快照值：plan_id 是弱引用（允许悬空），夹具不建真实套餐。
     */
    public Long createSubscription(Long userId, AgentType agentType, String name,
                    Instant startsAt, Instant endsAt, String credential) {
        SubscriptionDto s = new SubscriptionDto();
        s.setAssignmentNo(UUID.randomUUID().toString().replace("-", ""));
        s.setUserId(userId);
        s.setAgentType(agentType);
        s.setPlanId(1L);
        s.setName(name);
        s.setPlanDurationDays(30);
        s.setPlanPrice(new BigDecimal("10.00"));
        s.setPlanCurrency(Currency.USD);
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setCredential(credential);
        return subscriptionRepository.create(s);
    }

    /** 建一个「已开通可用」的用户：有节点、有一条在期 CLAUDE 订阅 */
    public Long createActiveUser(String subject, Long frontNodeId, Long landNodeId, String credential) {
        Long userId = createUser(subject, frontNodeId, landNodeId);
        createSubscription(userId, AgentType.CLAUDE, "Claude 席位",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(30, ChronoUnit.DAYS), credential);
        return userId;
    }

    /** 建一个订阅导入形态的 MIHOMO 节点（整份参数在 secret 里）；groupId 可为 null */
    public Long createMihomoNode(String name, Long groupId) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName(name);
        node.setRole(NodeRole.FRONT);
        node.setProtocol(NodeProtocol.MIHOMO);
        node.setServerAddr("hk01.example.com");
        node.setPort(35355);
        node.setSecret(Map.of("type", "anytls", "server", "hk01.example.com",
                "port", 35355, "password", "mihomo-密码"));
        node.setGroupId(groupId);
        node.setSourceName(name);
        node.setSourceType("anytls");
        return nodeRepository.create(node);
    }
}
