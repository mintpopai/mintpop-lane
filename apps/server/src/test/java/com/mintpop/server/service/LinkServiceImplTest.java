package com.mintpop.server.service;

import com.mintpop.server.config.LinkProperties;
import com.mintpop.server.dto.ProxyNodeDto;
import com.mintpop.server.dto.UserDto;
import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.enumeration.NodeProtocol;
import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.enumeration.NodeStatus;
import com.mintpop.server.enumeration.UserStatus;
import com.mintpop.server.exception.BizException;
import com.mintpop.server.repository.ProxyNodeRepository;
import com.mintpop.server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LinkServiceImplTest {

    private UserRepository userRepository;
    private ProxyNodeRepository nodeRepository;
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
        u.setId(1L);
        u.setSubject("u1");
        u.setName("张三");
        u.setStatus(status);
        u.setFrontNodeId(10L);
        u.setLandNodeId(20L);
        u.setClaudeCredential("sk-ant-test");
        return u;
    }

    @BeforeEach
    void 准备() {
        userRepository = mock(UserRepository.class);
        nodeRepository = mock(ProxyNodeRepository.class);

        LinkProperties props = new LinkProperties();
        props.setTtlSeconds(1800);
        service = new LinkServiceImpl(props, userRepository, nodeRepository);

        when(userRepository.findBySubject(any())).thenReturn(Optional.empty());
        when(nodeRepository.findById(10L))
                .thenReturn(Optional.of(node(10L, NodeRole.FRONT, NodeProtocol.TROJAN, "us.example.com")));
        when(nodeRepository.findById(20L))
                .thenReturn(Optional.of(node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10")));
    }

    private void 库里有(UserDto user) {
        when(userRepository.findBySubject("u1")).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("正常用户能拿到两跳链路与席位凭据")
    void 正常用户能拿到两跳链路() {
        库里有(user(UserStatus.ACTIVE));

        var resp = service.resolveLink("u1");

        assertThat(resp.front()).containsEntry("type", "trojan").containsEntry("server", "us.example.com");
        assertThat(resp.land()).containsEntry("type", "socks5").containsEntry("server", "203.0.113.10");
        assertThat(resp.expectedEgressIps()).containsExactly("203.0.113.10");
        assertThat(resp.claudeCredential()).isEqualTo("sk-ant-test");
        assertThat(resp.ttlSeconds()).isEqualTo(1800);
    }

    @Test
    @DisplayName("未录入的账号被拒绝")
    void 未录入的账号被拒绝() {
        assertThatThrownBy(() -> service.resolveLink("陌生人"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.ACCOUNT_NOT_ENROLLED);
    }

    @Test
    @DisplayName("已吊销的用户拿不到链路")
    void 已吊销的用户拿不到链路() {
        库里有(user(UserStatus.REVOKED));

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("已暂停的用户同样拿不到链路，判断条件是「非 ACTIVE」而不是只挡 REVOKED")
    void 已暂停的用户拿不到链路() {
        库里有(user(UserStatus.SUSPENDED));

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.LINK_REVOKED);
    }

    @Test
    @DisplayName("第一跳节点查不到（外键被绕过约束改坏）时按内部错误拒绝，不下发残缺链路")
    void 第一跳节点查不到时按内部错误拒绝() {
        库里有(user(UserStatus.ACTIVE));
        when(nodeRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.INTERNAL_ERROR);
    }

    @Test
    @DisplayName("未分配落地节点的用户被拒绝，避免客户端放弃出口校验")
    void 未分配落地节点的用户被拒绝() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setLandNodeId(null);
        库里有(u);

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("落地节点被禁用时不下发链路")
    void 落地节点被禁用时不下发链路() {
        库里有(user(UserStatus.ACTIVE));
        ProxyNodeDto disabled = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        disabled.setStatus(NodeStatus.DISABLED);
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_DISABLED);
    }

    @Test
    @DisplayName("第一跳节点被禁用时同样不下发链路，避免只守住半条 fail-closed 保障")
    void 第一跳节点被禁用时不下发链路() {
        库里有(user(UserStatus.ACTIVE));
        ProxyNodeDto disabled = node(10L, NodeRole.FRONT, NodeProtocol.TROJAN, "us.example.com");
        disabled.setStatus(NodeStatus.DISABLED);
        when(nodeRepository.findById(10L)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.NODE_DISABLED);
    }

    @Test
    @DisplayName("落地节点没有出口 IP 时被拒绝")
    void 落地节点没有出口ip时被拒绝() {
        库里有(user(UserStatus.ACTIVE));
        ProxyNodeDto noIp = node(20L, NodeRole.LAND, NodeProtocol.SOCKS5, "203.0.113.10");
        noIp.setEgressIps(List.of());
        when(nodeRepository.findById(20L)).thenReturn(Optional.of(noIp));

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.EGRESS_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("缺少席位凭据的用户被拒绝")
    void 缺少席位凭据的用户被拒绝() {
        UserDto u = user(UserStatus.ACTIVE);
        u.setClaudeCredential("  ");
        库里有(u);

        assertThatThrownBy(() -> service.resolveLink("u1"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("心跳如实返回用户状态")
    void 心跳如实返回用户状态() {
        库里有(user(UserStatus.ACTIVE));
        assertThat(service.heartbeat("u1").status()).isEqualTo(UserStatus.ACTIVE);

        库里有(user(UserStatus.SUSPENDED));
        assertThat(service.heartbeat("u1").status()).isEqualTo(UserStatus.SUSPENDED);
    }

    @Test
    @DisplayName("未录入账号的心跳按吊销处理，客户端据此断链")
    void 未录入账号的心跳按吊销处理() {
        assertThat(service.heartbeat("陌生人").status()).isEqualTo(UserStatus.REVOKED);
    }
}
