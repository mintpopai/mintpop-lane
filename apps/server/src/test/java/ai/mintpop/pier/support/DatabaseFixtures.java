package ai.mintpop.pier.support;

import ai.mintpop.pier.dto.ProxyNodeDto;
import ai.mintpop.pier.dto.UserDto;
import ai.mintpop.pier.enumeration.NodeProtocol;
import ai.mintpop.pier.enumeration.NodeRole;
import ai.mintpop.pier.enumeration.UserRole;
import ai.mintpop.pier.enumeration.UserStatus;
import ai.mintpop.pier.repository.ProxyNodeRepository;
import ai.mintpop.pier.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;

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

    public DatabaseFixtures(JdbcTemplate jdbc, ProxyNodeRepository nodeRepository, UserRepository userRepository) {
        this.jdbc = jdbc;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
    }

    /** 清空全部业务表。外键约束在清库期间临时关掉，顺序因此不敏感。 */
    public void 清空() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
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

    /** 建一个可用的普通用户 */
    public Long 建用户(String subject, Long frontNodeId, Long landNodeId) {
        return 建用户(subject, UserRole.MEMBER, UserStatus.ACTIVE, frontNodeId, landNodeId, "sk-ant-" + subject);
    }

    public Long 建用户(String subject, UserRole role, UserStatus status,
                    Long frontNodeId, Long landNodeId, String credential) {
        UserDto user = new UserDto();
        user.setSubject(subject);
        user.setName("测试" + subject);
        user.setRole(role);
        user.setStatus(status);
        user.setFrontNodeId(frontNodeId);
        user.setLandNodeId(landNodeId);
        user.setClaudeCredential(credential);
        return userRepository.create(user);
    }
}
