package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.NodeGroupImportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅导入：把节点的故障域一并解析入库")
class AdminNodeGroupServiceImplTest {

    @Mock private NodeGroupRepository groupRepository;
    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubFetchClient subFetchClient;
    @Mock private FailureDomainResolver failureDomainResolver;
    @Mock private TransactionTemplate transactionTemplate;

    private AdminNodeGroupServiceImpl service;

    private static final String SUB_YAML = """
            proxies:
              - { name: 'US-01', type: anytls, server: hk01a.t11-a.app, port: 35660, password: p }
            """;

    @BeforeEach
    void setUp() {
        // 事务模板在单测里退化成「立即执行回调」，不引入真事务管理器
        when(transactionTemplate.execute(any())).thenAnswer(inv ->
                inv.getArgument(0, TransactionCallback.class).doInTransaction(null));
        doAnswer(inv -> {
            inv.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult(SUB_YAML, null, null, null));
        when(nodeRepository.existsByName(anyString())).thenReturn(false);

        service = new AdminNodeGroupServiceImpl(groupRepository, nodeRepository, userRepository,
                subFetchClient, new SubYamlParser(), transactionTemplate, failureDomainResolver);
    }

    private NodeGroupDto group(long id) {
        NodeGroupDto group = new NodeGroupDto();
        group.setId(id);
        group.setName("候选机场");
        group.setSubUrl("https://example.com/sub?token=x");
        return group;
    }

    @Test
    @DisplayName("新建节点时写入解析到的故障域与解析时间")
    void writesFailureDomainOnCreate() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByGroupIdAndSourceName(1L, "US-01")).thenReturn(Optional.empty());
        when(failureDomainResolver.resolve("hk01a.t11-a.app")).thenReturn("hk.tsdns.top");

        NodeGroupImportRequest request = new NodeGroupImportRequest();
        request.setSelectedNames(List.of("US-01"));
        service.importNodes(1L, request);

        ArgumentCaptor<ProxyNodeDto> captor = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).create(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("hk.tsdns.top");
        assertThat(captor.getValue().getFailureDomainCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("解析失败时保留原有故障域，且不让导入失败")
    void keepsPreviousFailureDomainWhenResolveFails() {
        ProxyNodeDto existing = new ProxyNodeDto();
        existing.setId(7L);
        existing.setSourceName("US-01");
        existing.setFailureDomain("jp.tsdns.top");
        existing.setFailureDomainCheckedAt(Instant.parse("2026-09-01T00:00:00Z"));

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByGroupIdAndSourceName(1L, "US-01")).thenReturn(Optional.of(existing));
        when(failureDomainResolver.resolve(anyString())).thenReturn(null);

        NodeGroupImportRequest request = new NodeGroupImportRequest();
        request.setSelectedNames(List.of("US-01"));
        assertThatCode(() -> service.importNodes(1L, request)).doesNotThrowAnyException();

        ArgumentCaptor<ProxyNodeDto> captor = ArgumentCaptor.forClass(ProxyNodeDto.class);
        verify(nodeRepository).update(captor.capture());
        assertThat(captor.getValue().getFailureDomain()).isEqualTo("jp.tsdns.top");
        assertThat(captor.getValue().getFailureDomainCheckedAt())
                .isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("同一域名的多个节点只解析一次，不重复查 DNS")
    void resolvesEachServerAddrOnce() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group(1L)));
        when(nodeRepository.findByGroupIdAndSourceName(anyLong(), anyString())).thenReturn(Optional.empty());
        when(failureDomainResolver.resolve(anyString())).thenReturn("hk.tsdns.top");
        when(subFetchClient.fetch(anyString())).thenReturn(new SubFetchResult("""
                proxies:
                  - { name: 'US-01', type: anytls, server: hk01a.t11-a.app, port: 35660, password: p }
                  - { name: 'US-02', type: anytls, server: hk01a.t11-a.app, port: 35661, password: p }
                """, null, null, null));

        NodeGroupImportRequest request = new NodeGroupImportRequest();
        request.setSelectedNames(List.of("US-01", "US-02"));
        service.importNodes(1L, request);

        verify(failureDomainResolver, times(1)).resolve("hk01a.t11-a.app");
    }
}
