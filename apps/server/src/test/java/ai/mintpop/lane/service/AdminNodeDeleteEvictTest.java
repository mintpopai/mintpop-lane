package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("删除节点：订阅下的 FRONT 节点要逐出渲染缓存，落地节点不涉及")
class AdminNodeDeleteEvictTest {

    private final ProxyNodeRepository nodeRepository = mock(ProxyNodeRepository.class);
    private final SubscriptionRenderCache renderCache = mock(SubscriptionRenderCache.class);
    private AdminNodeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminNodeServiceImpl(nodeRepository, mock(UserRepository.class),
                mock(AirportSubscriptionRepository.class), land -> "unused", mock(NodeNotifyService.class), renderCache);
    }

    @Test
    @DisplayName("挂在订阅下的 FRONT 节点被删：逐出该订阅缓存")
    void deletingFrontNodeEvictsItsSubscription() {
        ProxyNodeDto front = new ProxyNodeDto();
        front.setId(5L);
        front.setRole(NodeRole.FRONT);
        front.setAirportSubscriptionId(9L);
        when(nodeRepository.findById(5L)).thenReturn(Optional.of(front));

        service.delete(5L);

        verify(nodeRepository).deleteById(5L);
        verify(renderCache).evict(9L);
    }

    @Test
    @DisplayName("落地节点被删：不逐出任何缓存")
    void deletingLandNodeDoesNotEvict() {
        ProxyNodeDto land = new ProxyNodeDto();
        land.setId(6L);
        land.setRole(NodeRole.LAND);
        when(nodeRepository.findById(6L)).thenReturn(Optional.of(land));

        service.delete(6L);

        verify(nodeRepository).deleteById(6L);
        verify(renderCache, never()).evict(anyLong());
    }
}
