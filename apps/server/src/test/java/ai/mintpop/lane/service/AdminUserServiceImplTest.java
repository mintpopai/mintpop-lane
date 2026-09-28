package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.response.AdminUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * failureDomainCount 的计算口径：管理员手工指定单个节点是保留的运维逃生口
 * （见 AdminUserServiceImpl.update），不走 FrontNodeAllocator 的分桶算法，
 * 完全可能挂着一个 failureDomain 尚未解析成功（NULL）的节点。这条口径必须
 * 「null 不算一个独立故障域」，否则 null 会被 distinct() 当成一个真实故障域，
 * 把「未知」误报成「有 2 个域」，掩盖了「其实一个都不确定」这个更糟的处境。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminUserServiceImpl：前置组与 failureDomainCount 的组装口径")
class AdminUserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserFrontNodeRepository userFrontNodeRepository;
    @Mock private FrontNodeAllocator frontNodeAllocator;
    @Mock private FrontSubscriptionService frontSubscriptionService;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);
        service = new AdminUserServiceImpl(userRepository, nodeRepository, subscriptionRepository,
                userFrontNodeRepository, frontNodeAllocator, frontSubscriptionService, clock);
    }

    private ProxyNodeDto frontNode(long id, String name, String failureDomain) {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setId(id);
        node.setName(name);
        node.setRole(NodeRole.FRONT);
        node.setFailureDomain(failureDomain);
        return node;
    }

    @Test
    @DisplayName("一个节点有 failureDomain、另一个为 null：null 不被计成独立故障域，failureDomainCount 仍是 1")
    void nullFailureDomainDoesNotCountAsItsOwnDomain() {
        UserDto user = new UserDto();
        user.setId(3L);
        user.setSubject("logto-3");
        user.setEmail("u3@test.example");
        user.setFrontNodeId(1L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        // 1 号带 failureDomain，2 号尚未解析出来（运维手工指定单节点时的真实场景）
        when(nodeRepository.findAll(null)).thenReturn(List.of(
                frontNode(1, "🇺🇸[US]A1", "jp.tsdns.top"),
                frontNode(2, "🇺🇸[US]A2", null)));
        when(subscriptionRepository.findByUserId(3L)).thenReturn(List.of());
        when(userFrontNodeRepository.findNodeIdsByUserId(3L)).thenReturn(List.of(1L, 2L));

        AdminUserResponse response = service.get(3L);

        assertThat(response.frontNodes()).hasSize(2);
        assertThat(response.failureDomainCount()).isEqualTo(1);
    }
}
