package ai.mintpop.lane.support;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    public void 清空() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbc.execute("TRUNCATE TABLE subscription");
        jdbc.execute("TRUNCATE TABLE app_user");
        jdbc.execute("TRUNCATE TABLE proxy_node");
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /** 建一个 trojan 协议的第一跳节点 */
    public Long 建FRONT节点(String name) {
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

    /** 建一个 socks5 协议的落地节点，出口 IP 由调用方指定 */
    public Long 建LAND节点(String name, String egressIp) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setName(name);
        node.setRole(NodeRole.LAND);
        node.setProtocol(NodeProtocol.SOCKS5);
        node.setServerAddr(egressIp);
        node.setPort(50101);
        node.setSecret(Map.of("username", "land-用户名", "password", "land-密码"));
        node.setEgressIps(List.of(egressIp));
        return nodeRepository.create(node);
    }

    /** 直接读原始列，用于断言库里存的确实是密文 */
    public String 读原始密文列(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id);
    }

    /** 建一个普通用户（无订阅、无凭据） */
    public Long 建用户(String subject, Long frontNodeId, Long landNodeId) {
        return 建用户(subject, UserRole.MEMBER, UserStatus.ACTIVE, frontNodeId, landNodeId);
    }

    public Long 建用户(String subject, UserRole role, UserStatus status, Long frontNodeId, Long landNodeId) {
        UserDto user = new UserDto();
        user.setSubject(subject);
        user.setEmail(subject + "@test.example");
        user.setName("测试" + subject);
        user.setRole(role);
        user.setStatus(status);
        user.setFrontNodeId(frontNodeId);
        user.setLandNodeId(landNodeId);
        return userRepository.create(user);
    }

    /** 给用户建一条订阅；credential 传 null 表示未录入凭据 */
    public Long 建订阅(Long userId, AgentType agentType, String name,
                    LocalDateTime startsAt, LocalDateTime endsAt, String credential) {
        SubscriptionDto s = new SubscriptionDto();
        s.setUserId(userId);
        s.setAgentType(agentType);
        s.setName(name);
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setCredential(credential);
        return subscriptionRepository.create(s);
    }

    /** 建一个「已开通可用」的用户：有节点、有一条在期 CLAUDE 订阅 */
    public Long 建可用用户(String subject, Long frontNodeId, Long landNodeId, String credential) {
        Long userId = 建用户(subject, frontNodeId, landNodeId);
        建订阅(userId, AgentType.CLAUDE, "Claude 席位",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), credential);
        return userId;
    }
}
