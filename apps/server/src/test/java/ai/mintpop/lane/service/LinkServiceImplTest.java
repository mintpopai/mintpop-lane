package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.entity.DeviceRebindRequest;
import ai.mintpop.lane.entity.UserDevice;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.DeviceBinding;
import ai.mintpop.lane.enumeration.LinkStatus;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.NodeStatus;
import ai.mintpop.lane.enumeration.RebindRequestStatus;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.DeviceRebindRequestRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserDeviceRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.LinkConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    private static final Long MISSING_USER_ID = 999999L;
    /** 全类统一的「现在」，service 用固定时钟构造，判定完全确定 */
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final String THIS_DEVICE = "a".repeat(64);
    private static final String OTHER_DEVICE = "b".repeat(64);

    private UserRepository userRepository;
    private ProxyNodeRepository nodeRepository;
    private SubscriptionRepository subscriptionRepository;
    private UserDeviceRepository userDeviceRepository;
    private DeviceRebindRequestRepository rebindRequestRepository;
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
            n.setEgressIp("203.0.113.10");
            n.setEgressTimezone("Asia/Tokyo");
        }
        return n;
    }

    private static UserDto user(UserStatus status) {
        UserDto u = new UserDto();
        u.setId(USER_ID);
        u.setSubject("u1");
        u.setEmail("u1@test.example");
        u.setStatus(status);
        u.setFrontNodeId(10L);
        u.setLandNodeId(20L);
        return u;
    }

    /** 造一条订阅：在期传 now-1d ~ now+30d，过期传 now-30d ~ now-1d */
    private static SubscriptionDto subscription(Long id, AgentType agentType, String name,
                                                 Instant startsAt, Instant endsAt, String credential) {
        SubscriptionDto s = new SubscriptionDto();
        s.setId(id);
        // 分配号取一个固定短码即可：本测试只关心它是否被原样透传给客户端
        s.setAssignmentNo("7K3M9QX2FT");
        s.setUserId(USER_ID);
        s.setAgentType(agentType);
        s.setName(name);
        s.setStartsAt(startsAt);
        s.setEndsAt(endsAt);
        s.setCredential(credential);
        return s;
    }

    private static SubscriptionDto activeSubscription(Long id, String credential) {
        return subscription(id, AgentType.CLAUDE, "Claude 席位",
                NOW.minus(1, ChronoUnit.DAYS), NOW.plus(30, ChronoUnit.DAYS), credential);
    }

    private static SubscriptionDto expiredSubscription(Long id, String credential) {
        return subscription(id, AgentType.CLAUDE, "Claude 席位",
                NOW.minus(30, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS), credential);
    }

    private static UserDevice device(Long id, String deviceId, String name) {
        UserDevice d = new UserDevice();
        d.setId(id);
        d.setUserId(USER_ID);
        d.setDeviceId(deviceId);
        d.setName(name);
        d.setOs("macos 26.6.1");
        d.setModel("Mac17,9");
        return d;
    }

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        nodeRepository = mock(ProxyNodeRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        userDeviceRepository = mock(UserDeviceRepository.class);
        rebindRequestRepository = mock(DeviceRebindRequestRepository.class);

        LinkProperties props = new LinkProperties();
        props.setTtlSeconds(1800);
        service = new LinkServiceImpl(props, userRepository, nodeRepository, subscriptionRepository,
                userDeviceRepository, rebindRequestRepository, Clock.fixed(NOW, ZoneOffset.UTC));

        when(userRepository.findById(any())).thenReturn(Optional.empty());
        when(nodeRepository.findById(10L))
                .thenReturn(Optional.of(node(10L, NodeRole.FRONT, NodeProtocol.TROJAN, "us.example.com")));
        when(nodeRepository.findById(20L))
                .thenReturn(Optional.of(node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10")));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(userDeviceRepository.findByUserId(any())).thenReturn(List.of());
        when(rebindRequestRepository.findPendingByUserId(any())).thenReturn(List.of());
    }

    private void givenUser(UserDto user) {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    private void givenSubscriptions(SubscriptionDto... subscriptions) {
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscriptions));
    }

    /**
     * 登记本机为已知设备（行 id 固定 9L），供只关心「凭据能否下发」而非绑定关系本身的
     * 既有用例复用——这些用例的订阅要先 setBoundDeviceId(9L) 绑到本机，凭据才会下发。
     */
    private void givenThisDeviceKnown() {
        when(userDeviceRepository.findByUserId(USER_ID))
                .thenReturn(List.of(device(9L, THIS_DEVICE, "本机")));
    }

    @Test
    @DisplayName("正常用户能拿到两跳链路与在期订阅的凭据")
    void activeUserGetsTwoHopLink() {
        givenUser(user(UserStatus.ACTIVE));
        givenThisDeviceKnown();
        SubscriptionDto sub = activeSubscription(100L, "sk-ant-test");
        sub.setBoundDeviceId(9L);
        givenSubscriptions(sub);

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.front()).containsEntry("type", "trojan").containsEntry("server", "us.example.com");
        assertThat(resp.land()).containsEntry("type", "socks5").containsEntry("server", "203.0.113.10");
        assertThat(resp.expectedEgressIp()).isEqualTo("203.0.113.10");
        assertThat(resp.agentCredentials()).hasSize(1);
        assertThat(resp.agentCredentials().getFirst().credential()).isEqualTo("sk-ant-test");
        assertThat(resp.agentCredentials().getFirst().agentType()).isEqualTo(AgentType.CLAUDE);
        assertThat(resp.agentCredentials().getFirst().assignmentNo()).isEqualTo("7K3M9QX2FT");
        assertThat(resp.ttlSeconds()).isEqualTo(1800);
    }

    @Test
    @DisplayName("落地节点录了出口时区就随链路配置下发")
    void egressTimezoneDeliveredWithLink() {
        givenUser(user(UserStatus.ACTIVE));

        assertThat(service.resolveLink(USER_ID, THIS_DEVICE).egressTimezone()).isEqualTo("Asia/Tokyo");
    }

    @Test
    @DisplayName("落地节点没录时区时下发 null，不拦建链——时区是增强信息不是前置条件")
    void missingEgressTimezoneDeliversNullWithoutBlocking() {
        ProxyNodeDto land = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        land.setEgressTimezone(null);
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(land));
        givenUser(user(UserStatus.ACTIVE));

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.egressTimezone()).isNull();
        assertThat(resp.expectedEgressIp()).isNotBlank();
    }

    @Test
    @DisplayName("未录入的账号被拒绝，按吊销处理")
    void unknownAccountRejected() {
        assertThatThrownBy(() -> service.resolveLink(MISSING_USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("已吊销的用户拿不到链路")
    void revokedUserRejected() {
        givenUser(user(UserStatus.REVOKED));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("已暂停的用户同样拿不到链路，判断条件是「非 ACTIVE」而不是只挡 REVOKED")
    void suspendedUserRejected() {
        givenUser(user(UserStatus.SUSPENDED));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("从未购买过服务但配好网络的用户仍下发链路，席位为空——套餐与网络配置解耦")
    void neverPurchasedStillDeliversLinkWithEmptySeats() {
        givenUser(user(UserStatus.ACTIVE));
        // 订阅列表默认空，无需额外造数

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials()).isEmpty();
        assertThat(resp.expectedEgressIp()).isNotBlank();
    }

    @Test
    @DisplayName("订阅全部过期时仍下发链路，过期订阅的凭据不下发")
    void allSubscriptionsExpiredStillDeliversLinkWithEmptySeats() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(expiredSubscription(100L, "sk-ant-test"));

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials()).isEmpty();
        assertThat(resp.expectedEgressIp()).isNotBlank();
    }

    @Test
    @DisplayName("没买过服务且未分配链路资源时，按「资源未分配」拒绝——缺的是网络配置，不是套餐")
    void neverPurchasedWithoutNodesRejectedAsEgressNotAssigned() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setFrontNodeId(null);
        u.setLandNodeId(null);
        givenUser(u);

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("有在期订阅但未分配链路资源时被拒绝")
    void activeSubscriptionButNoNodesRejected() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setFrontNodeId(null);
        u.setLandNodeId(null);
        givenUser(u);
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("只分配了第一跳、没分配落地节点时同样被拒绝")
    void frontOnlyWithoutLandRejected() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setLandNodeId(null);
        givenUser(u);
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("第一跳节点查不到（外键被绕过约束改坏）时按内部错误拒绝，不下发残缺链路")
    void missingFrontNodeRejectedAsInternalError() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));
        when(nodeRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("落地节点被禁用时不下发链路")
    void disabledLandNodeRejected() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));
        ProxyNodeDto disabled = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        disabled.setStatus(NodeStatus.DISABLED);
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_DISABLED);
    }

    @Test
    @DisplayName("第一跳节点被禁用时同样不下发链路，避免只守住半条 fail-closed 保障")
    void disabledFrontNodeRejected() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));
        ProxyNodeDto disabled = node(10L, NodeRole.FRONT, NodeProtocol.TROJAN, "us.example.com");
        disabled.setStatus(NodeStatus.DISABLED);
        when(nodeRepository.findById(10L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_DISABLED);
    }

    @Test
    @DisplayName("落地节点没有出口 IP 时被拒绝")
    void landNodeWithoutEgressIpRejected() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));
        ProxyNodeDto noIp = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        noIp.setEgressIp(null);
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(noIp));

        assertThatThrownBy(() -> service.resolveLink(USER_ID, THIS_DEVICE))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("在期订阅全部无凭据时仍下发链路，席位列表为空——凭据只影响会话，不拦建链")
    void noCredentialStillDeliversLinkWithEmptySeats() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, null));

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials()).isEmpty();
        assertThat(resp.expectedEgressIp()).isNotBlank();
    }

    @Test
    @DisplayName("在期订阅全部无凭据时仍下发链路——空白字符串同样算未录入")
    void blankCredentialStillDeliversLinkWithEmptySeats() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, "   "));

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials()).isEmpty();
        assertThat(resp.expectedEgressIp()).isNotBlank();
    }

    @Test
    @DisplayName("两条在期订阅中一条无凭据，只下发有凭据的那一条")
    void onlyCredentialedSubscriptionDelivered() {
        givenUser(user(UserStatus.ACTIVE));
        givenThisDeviceKnown();
        SubscriptionDto withCredential = activeSubscription(100L, "sk-ant-有凭据");
        withCredential.setBoundDeviceId(9L);
        givenSubscriptions(withCredential, activeSubscription(101L, null));

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials()).hasSize(1);
        assertThat(resp.agentCredentials().getFirst().credential()).isEqualTo("sk-ant-有凭据");
        assertThat(resp.agentCredentials().getFirst().subscriptionId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("下发席位时带上凭证 scope，客户端据此决定要不要注入 scope 环境变量")
    void carriesCredentialScope() {
        givenUser(user(UserStatus.ACTIVE));
        givenThisDeviceKnown();
        SubscriptionDto subscription = activeSubscription(100L, "sk-ant-test");
        subscription.setCredentialScope("user:inference user:profile");
        subscription.setBoundDeviceId(9L);
        givenSubscriptions(subscription);

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials()).hasSize(1);
        assertThat(resp.agentCredentials().getFirst().credentialScope())
                .isEqualTo("user:inference user:profile");
    }

    @Test
    @DisplayName("旧式凭证没有 scope，下发空串而非 null：客户端按空串整段跳过注入")
    void legacyCredentialCarriesEmptyScope() {
        givenUser(user(UserStatus.ACTIVE));
        SubscriptionDto subscription = activeSubscription(100L, "sk-ant-test");
        subscription.setCredentialScope(null);
        givenSubscriptions(subscription);

        var resp = service.resolveLink(USER_ID, THIS_DEVICE);

        assertThat(resp.agentCredentials().getFirst().credentialScope()).isEmpty();
    }

    @Test
    @DisplayName("心跳：正常用户返回 ACTIVE")
    void heartbeatActiveUserReturnsActive() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(activeSubscription(100L, "sk-ant-test"));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("心跳：已暂停用户返回 SUSPENDED")
    void heartbeatSuspendedUserReturnsSuspended() {
        givenUser(user(UserStatus.SUSPENDED));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.SUSPENDED);
    }

    @Test
    @DisplayName("心跳：已吊销用户返回 REVOKED")
    void heartbeatRevokedUserReturnsRevoked() {
        givenUser(user(UserStatus.REVOKED));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.REVOKED);
    }

    @Test
    @DisplayName("心跳：订阅全部过期不影响链路，仍返回 ACTIVE——套餐只影响席位，不拦网络")
    void heartbeatAllExpiredStillReturnsActive() {
        givenUser(user(UserStatus.ACTIVE));
        givenSubscriptions(expiredSubscription(100L, "sk-ant-test"));

        assertThat(service.heartbeat(USER_ID).status()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("心跳：未录入账号按吊销处理，客户端据此断链")
    void heartbeatUnknownAccountTreatedAsRevoked() {
        assertThat(service.heartbeat(MISSING_USER_ID).status()).isEqualTo(LinkStatus.REVOKED);
    }

    @Test
    @DisplayName("未绑定的席位：标 UNBOUND 且凭据置空——绑定之前谁也拿不到")
    void unboundSubscriptionGetsNoCredential() {
        givenUser(user(UserStatus.ACTIVE));
        SubscriptionDto sub = activeSubscription(1L, "sk-ant-secret");
        sub.setBoundDeviceId(null);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(rebindRequestRepository.findPendingByUserId(USER_ID)).thenReturn(List.of());

        LinkConfigResponse.AgentCredential row = service.resolveLink(USER_ID, THIS_DEVICE)
                .agentCredentials().getFirst();

        assertThat(row.deviceBinding()).isEqualTo(DeviceBinding.UNBOUND);
        assertThat(row.credential()).isEmpty();
    }

    @Test
    @DisplayName("绑在本机的席位：标 BOUND_HERE 且照常下发凭据")
    void boundHereGetsCredential() {
        givenUser(user(UserStatus.ACTIVE));
        SubscriptionDto sub = activeSubscription(1L, "sk-ant-secret");
        sub.setBoundDeviceId(7L);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));
        when(userDeviceRepository.findByUserId(USER_ID))
                .thenReturn(List.of(device(7L, THIS_DEVICE, "月白的 MacBook")));
        when(rebindRequestRepository.findPendingByUserId(USER_ID)).thenReturn(List.of());

        LinkConfigResponse.AgentCredential row = service.resolveLink(USER_ID, THIS_DEVICE)
                .agentCredentials().getFirst();

        assertThat(row.deviceBinding()).isEqualTo(DeviceBinding.BOUND_HERE);
        assertThat(row.credential()).isEqualTo("sk-ant-secret");
    }

    @Test
    @DisplayName("绑在别处的席位：凭据置空，但席位照列并带上那台机器的名字")
    void boundElsewhereIsListedWithoutCredential() {
        givenUser(user(UserStatus.ACTIVE));
        SubscriptionDto sub = activeSubscription(1L, "sk-ant-secret");
        sub.setBoundDeviceId(8L);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));
        when(userDeviceRepository.findByUserId(USER_ID))
                .thenReturn(List.of(device(8L, OTHER_DEVICE, "办公室 iMac")));
        when(rebindRequestRepository.findPendingByUserId(USER_ID)).thenReturn(List.of());

        LinkConfigResponse.AgentCredential row = service.resolveLink(USER_ID, THIS_DEVICE)
                .agentCredentials().getFirst();

        // 席位必须照列：滤掉它，用户只会看到「我明明买了，席位却凭空消失」，
        // 而真正的原因（绑在办公室那台机器上）没有任何地方说得出来
        assertThat(row.deviceBinding()).isEqualTo(DeviceBinding.BOUND_ELSEWHERE);
        assertThat(row.credential()).isEmpty();
        assertThat(row.boundDeviceName()).isEqualTo("办公室 iMac");
        assertThat(row.pendingRequest()).isFalse();
    }

    @Test
    @DisplayName("本机已为该订阅提过申请且未被处理时，pendingRequest 为真")
    void pendingRequestFromThisDeviceIsReported() {
        givenUser(user(UserStatus.ACTIVE));
        SubscriptionDto sub = activeSubscription(1L, "sk-ant-secret");
        sub.setBoundDeviceId(8L);
        DeviceRebindRequest pending = new DeviceRebindRequest();
        pending.setSubscriptionId(1L);
        pending.setToDeviceId(7L);
        pending.setStatus(RebindRequestStatus.PENDING);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(sub));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of(
                device(7L, THIS_DEVICE, "月白的 MacBook"),
                device(8L, OTHER_DEVICE, "办公室 iMac")));
        when(rebindRequestRepository.findPendingByUserId(USER_ID)).thenReturn(List.of(pending));

        LinkConfigResponse.AgentCredential row = service.resolveLink(USER_ID, THIS_DEVICE)
                .agentCredentials().getFirst();

        assertThat(row.pendingRequest()).isTrue();
    }

    @Test
    @DisplayName("绑定关系不改变既有的两道过滤：过期的、没录凭据的订阅仍然不下发")
    void bindingDoesNotWeakenExistingFilters() {
        givenUser(user(UserStatus.ACTIVE));
        SubscriptionDto expired = expiredSubscription(1L, "sk-ant-a");
        SubscriptionDto noCredential = activeSubscription(2L, null);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(expired, noCredential));
        when(userDeviceRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(rebindRequestRepository.findPendingByUserId(USER_ID)).thenReturn(List.of());

        assertThat(service.resolveLink(USER_ID, THIS_DEVICE).agentCredentials()).isEmpty();
    }
}
