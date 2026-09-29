package ai.mintpop.lane.service;

import ai.mintpop.lane.config.FrontTuningProperties;
import ai.mintpop.lane.dto.FrontSettings;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRegion;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.service.SubscriptionRenderCache.RenderedSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅级渲染缓存：命中不查库，逐出后重查，渲染叠保活覆盖并按地区过滤")
class InMemorySubscriptionRenderCacheTest {

    @Mock private ProxyNodeRepository nodeRepository;
    @Mock private SystemSettingService systemSettingService;
    private InMemorySubscriptionRenderCache cache;

    private static ProxyNodeDto node(long id, String sourceName, String domain) {
        ProxyNodeDto n = new ProxyNodeDto();
        n.setId(id);
        n.setRole(NodeRole.FRONT);
        n.setProtocol(NodeProtocol.MIHOMO);
        n.setSourceName(sourceName);
        n.setSourceType("anytls");
        n.setSecret(Map.of("type", "anytls", "server", "s", "port", 1, "password", "p"));
        n.setFailureDomain(domain);
        return n;
    }

    @BeforeEach
    void setUp() {
        FrontTuningProperties tuning = new FrontTuningProperties();
        tuning.setProtocols(Map.of("anytls", Map.of("idle-session-timeout", 300)));
        when(systemSettingService.frontSettings()).thenReturn(new FrontSettings(NodeRegion.US, 3, 20));
        cache = new InMemorySubscriptionRenderCache(nodeRepository, tuning, systemSettingService);
    }

    @Test
    @DisplayName("渲染：只留当前地区节点、叠上保活覆盖、故障域取众数")
    void rendersRegionNodesWithTuning() {
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(
                node(1, "🇺🇸[US]A", "d1"), node(2, "🇺🇸[US]B", "d1"), node(3, "🇺🇸[US]C", "d2"), node(4, "🇭🇰[HK]X", "d9")));

        RenderedSubscription rendered = cache.get(1L);

        assertThat(rendered.nodes()).hasSize(3);
        assertThat(rendered.nodes().get(0)).containsEntry("idle-session-timeout", 300).containsEntry("password", "p");
        assertThat(rendered.failureDomain()).isEqualTo("d1");
    }

    @Test
    @DisplayName("第二次 get 不再查库；evict 后再查一次；evictAll 清空全部")
    void cachesUntilEvicted() {
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(node(1, "🇺🇸[US]A", null)));

        cache.get(1L);
        cache.get(1L);
        verify(nodeRepository, times(1)).findByAirportSubscriptionId(1L);

        cache.evict(1L);
        cache.get(1L);
        verify(nodeRepository, times(2)).findByAirportSubscriptionId(1L);

        cache.evictAll();
        cache.get(1L);
        verify(nodeRepository, times(3)).findByAirportSubscriptionId(1L);
    }

    @Test
    @DisplayName("订阅下没有节点：nodes 为空、failureDomain 为 null，同样被缓存")
    void emptySubscriptionRendersEmpty() {
        when(nodeRepository.findByAirportSubscriptionId(2L)).thenReturn(List.of());
        RenderedSubscription rendered = cache.get(2L);
        assertThat(rendered.nodes()).isEmpty();
        assertThat(rendered.failureDomain()).isNull();
    }

    @Test
    @DisplayName("事务内 evict：提交前空档里重新缓存的旧渲染，提交后被再次逐出")
    void evictAgainAfterCommit() {
        when(nodeRepository.findByAirportSubscriptionId(1L)).thenReturn(List.of(node(1, "🇺🇸[US]A", null)));
        cache.get(1L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict(1L);
            cache.get(1L);   // 提交前空档：读到旧数据并缓存
            verify(nodeRepository, times(2)).findByAirportSubscriptionId(1L);

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        cache.get(1L);
        verify(nodeRepository, times(3)).findByAirportSubscriptionId(1L);
    }
}
