package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.LinkStatus;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LinkServiceImplTest {

    private static final Long USER_ID = 1L;
    /** 未使用的 id，代表库里查不到的用户 */
    private static final Long 不存在的用户ID = 999999L;

    private UserRepository userRepository;
    private ProxyNodeRepository nodeRepository;
    private SubscriptionRepository subscriptionRepository;
    private LinkServiceImpl service;

    private static ProxyNodeDto node(long id, NodeRole role, NodeProtocol protocol, String server) {
        ProxyNodeDto n = new ProxyNodeDto();
        n.setId(id);
        n.setRole(role);
        n.setProtocol(protocol);
        n.setServerAddr(server);
        n.setPort(443);
        n.setSecret(Map.of("password", "节点密码"));
        n.setStatus(NodeStatus.ENABLED);
        if (role == NodeRole.LAND) {
            n.setEgressIps(List.of("203.0.113.10"));
        }
        return n;
    }

    private static UserDto user(UserStatus status) {
        UserDto u = new UserDto();
        u.setId(USER_ID);
        u.setSubject("u1");
        u.setEmail("u1@test.example");
        u.setName("张三");
        u.setStatus(status);
        u.setFrontNodeId(10L);
        u.setLandNodeId(20L);
        return u;
    }

    /** 造一条订阅：在期传 now-1d ~ now+30d，过期传 now-30d ~ now-1d */
    private static SubscriptionDto subscription(Long id, AgentType agentType, String name,
                                                 LocalDateTime startsAt, LocalDateTime endsAt, String credential) {
        SubscriptionDto s = new SubscriptionDto();
        s.setId(id);
        s.setUserId(USER_ID);
        s.setAgentType(agentType);
        s.setName(name);
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setCredential(credential);
        return s;
    }

    private static SubscriptionDto 在期订阅(Long id, String credential) {
        LocalDateTime now = LocalDateTime.now();
        return subscription(id, AgentType.CLAUDE, "Claude 席位", now.minusDays(1), now.plusDays(30), credential);
    }

    private static SubscriptionDto 过期订阅(Long id, String credential) {
        LocalDateTime now = LocalDateTime.now();
        return subscription(id, AgentType.CLAUDE, "Claude 席位", now.minusDays(30), now.minusDays(1), credential);
    }

    @BeforeEach
    void 准备() {
        userRepository = mock(UserRepository.class);
        nodeRepository = mock(ProxyNodeRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);

        LinkProperties props = new LinkProperties();
        props.setTtlSeconds(1800);
        service = new LinkServiceImpl(props, userRepository, nodeRepository, subscriptionRepository);

        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(nodeRepository.findById(10L))
                .thenReturn(Optional.of(node(10L, NodeRole.FRONT, NodeProtocol.TROJAN, "us.example.com")));
        when(nodeRepository.findById(20L))
                .thenReturn(Optional.of(node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10")));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of());
    }

    private void 库里有(UserDto user) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void 订阅有(SubscriptionDto... subscriptions) {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscriptions));
    }

    @Test
    @DisplayName("正常用户能拿到两跳链路与在期订阅的凭据")
    void 正常用户能拿到两跳链路() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-test"));

        var resp = service.resolveLink(USER_ID);

        assertThat(resp.front()).containsEntry("type", "trojan").containsEntry("server", "us.example.com");
        assertThat(resp.land()).containsEntry("type", "socks5").containsEntry("server", "203.0.113.10");
        assertThat(resp.expectedEgressIps()).containsExactly("203.0.113.10");
        assertThat(resp.agentCredentials()).hasSize(1);
        assertThat(resp.agentCredentials().getFirst().credential()).isEqualTo("sk-ant-test");
        assertThat(resp.agentCredentials().getFirst().agentType()).isEqualTo(AgentType.CLAUDE);
        assertThat(resp.ttlSeconds()).isEqualTo(1800);
    }

    @Test
    @DisplayName("未录入的账号被拒绝，按吊销处理")
    void 未录入的账号被拒绝() {
        assertThatThrownBy(() -> service.resolveLink(不存在的用户ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("已吊销的用户拿不到链路")
    void 已吊销的用户拿不到链路() {
        库里有(user(UserStatus.REVOKED));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("已暂停的用户同样拿不到链路，判断条件是「非 ACTIVE」而不是只挡 REVOKED")
    void 已暂停的用户拿不到链路() {
        库里有(user(UserStatus.SUSPENDED));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("从未购买过服务的用户被拒绝")
    void 从未购买过服务的用户被拒绝() {
        库里有(user(UserStatus.ACTIVE));
        // 订阅列表默认空，无需额外造数

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.SERVICE_NOT_PURCHASED);
    }

    @Test
    @DisplayName("买过但订阅全部过期的用户被拒绝，文案区别于「从未购买」")
    void 订阅全部过期的用户被拒绝() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(过期订阅(100L, "sk-ant-test"));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.SERVICE_EXPIRED);
    }

    @Test
    @DisplayName("有在期订阅但未分配链路资源时被拒绝")
    void 有在期订阅但未分配链路资源时被拒绝() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setFrontNodeId(null);
        u.setLandNodeId(null);
        库里有(u);
        订阅有(在期订阅(100L, "sk-ant-test"));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("只分配了第一跳、没分配落地节点时同样被拒绝")
    void 只分配第一跳未分配落地时被拒绝() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setLandNodeId(null);
        库里有(u);
        订阅有(在期订阅(100L, "sk-ant-test"));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("第一跳节点查不到（外键被绕过约束改坏）时按内部错误拒绝，不下发残缺链路")
    void 第一跳节点查不到时按内部错误拒绝() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-test"));
        when(nodeRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("落地节点被禁用时不下发链路")
    void 落地节点被禁用时不下发链路() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-test"));
        ProxyNodeDto disabled = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        disabled.setStatus(NodeStatus.DISABLED);
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_DISABLED);
    }

    @Test
    @DisplayName("第一跳节点被禁用时同样不下发链路，避免只守住半条 fail-closed 保障")
    void 第一跳节点被禁用时不下发链路() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-test"));
        ProxyNodeDto disabled = node(10L, NodeRole.FRONT, NodeProtocol.TROJAN, "us.example.com");
        disabled.setStatus(NodeStatus.DISABLED);
        when(nodeRepository.findById(10L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_DISABLED);
    }

    @Test
    @DisplayName("落地节点没有出口 IP 时被拒绝")
    void 落地节点没有出口ip时被拒绝() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-test"));
        ProxyNodeDto noIp = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        noIp.setEgressIps(List.of());
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(noIp));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("在期订阅全部无凭据时被拒绝")
    void 在期订阅全部无凭据时被拒绝() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, null));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("在期订阅全部无凭据时被拒绝——空白字符串同样算未录入")
    void 在期订阅凭据为空白时被拒绝() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "   "));

        assertThatThrownBy(() -> service.resolveLink(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("两条在期订阅中一条无凭据，只下发有凭据的那一条")
    void 两条在期一条无凭据时只下发一条() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-有凭据"), 在期订阅(101L, null));

        var resp = service.resolveLink(USER_ID);

        assertThat(resp.agentCredentials()).hasSize(1);
        assertThat(resp.agentCredentials().getFirst().credential()).isEqualTo("sk-ant-有凭据");
        assertThat(resp.agentCredentials().getFirst().subscriptionId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("心跳：正常用户返回 ACTIVE")
    void 心跳正常用户返回ACTIVE() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(在期订阅(100L, "sk-ant-test"));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("心跳：已暂停用户返回 SUSPENDED")
    void 心跳已暂停用户返回SUSPENDED() {
        库里有(user(UserStatus.SUSPENDED));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.SUSPENDED);
    }

    @Test
    @DisplayName("心跳：已吊销用户返回 REVOKED")
    void 心跳已吊销用户返回REVOKED() {
        库里有(user(UserStatus.REVOKED));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.REVOKED);
    }

    @Test
    @DisplayName("心跳：处置态正常但在期订阅归零时返回 EXPIRED，保留登录态")
    void 心跳订阅全过期返回EXPIRED() {
        库里有(user(UserStatus.ACTIVE));
        订阅有(过期订阅(100L, "sk-ant-test"));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.EXPIRED);
    }

    @Test
    @DisplayName("心跳：未录入账号按吊销处理，客户端据此断链")
    void 心跳未录入账号按吊销处理() {
        assertThat(service.heartbeat(不存在的用户ID).status()).isEqualTo(LinkStatus.REVOKED);
    }
}
