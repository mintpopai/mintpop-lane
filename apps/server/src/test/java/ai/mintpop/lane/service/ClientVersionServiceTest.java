package ai.mintpop.lane.service;

import ai.mintpop.lane.client.LatestClientVersionClient;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.util.ClientVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientVersionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");

    private LatestClientVersionClient latestClient;
    private Clock clock;
    private ClientVersionService service;

    @BeforeEach
    void setUp() {
        latestClient = mock(LatestClientVersionClient.class);
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        service = new ClientVersionService(latestClient, clock);
    }

    private void latestIs(String version) {
        when(latestClient.fetchLatest()).thenReturn(ClientVersion.parse(version));
        service.refresh();
    }

    private static void assertOutdated(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CLIENT_VERSION_OUTDATED);
    }

    @Test
    @DisplayName("落后于最新版：拦下")
    void olderVersionRejected() {
        latestIs("1.2.0");
        assertOutdated(() -> service.requireCurrent("1.1.9"));
        assertOutdated(() -> service.requireCurrent("1.2.0-rc.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.0", "v1.2.0", "1.2.1", "2.0.0"})
    @DisplayName("等于或高于最新版：放行（本地开发时客户端可能先于清单升了版本号）")
    void currentOrNewerPasses(String version) {
        latestIs("1.2.0");
        assertThatCode(() -> service.requireCurrent(version)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "abc", "1.2"})
    @DisplayName("不带版本头或形状不对：一律按旧版拦下，哪怕还不知道最新版是多少")
    void missingOrMalformedAlwaysRejected(String version) {
        assertThat(service.latest()).isEmpty();
        assertOutdated(() -> service.requireCurrent(version));
    }

    @Test
    @DisplayName("从未拉到过最新版：带了合法版本号的一律放行，不能凭空判谁过期")
    void unknownLatestLetsVersionedClientsThrough() {
        when(latestClient.fetchLatest()).thenReturn(Optional.empty());
        service.refresh();
        assertThatCode(() -> service.requireCurrent("0.0.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("拉取失败沿用上一次的值：清单源抖一下不放过旧版，也不误伤新版")
    void fetchFailureKeepsLastKnown() {
        latestIs("1.2.0");
        when(latestClient.fetchLatest()).thenReturn(Optional.empty());
        service.refresh();

        assertThat(service.latest()).contains(new ClientVersion(1, 2, 0, null));
        assertOutdated(() -> service.requireCurrent("1.1.0"));
    }

    @Test
    @DisplayName("发了新版：下一轮拉取后旧的当前版即被拦下")
    void newReleaseTakesEffectOnNextRefresh() {
        latestIs("1.1.0");
        assertThatCode(() -> service.requireCurrent("1.1.0")).doesNotThrowAnyException();

        latestIs("1.2.0");
        assertOutdated(() -> service.requireCurrent("1.1.0"));
    }

    @Test
    @DisplayName("状态：成功时记下版本与拉取时刻，失败时沿用版本、只更新尝试时刻并标记失败")
    void statusTracksSuccessAndFailure() {
        assertThat(service.status()).isEqualTo(new ClientVersionService.Status(null, null, null, false));

        when(latestClient.fetchLatest()).thenReturn(ClientVersion.parse("1.2.0"));
        assertThat(service.refresh()).isTrue();
        assertThat(service.status()).isEqualTo(
                new ClientVersionService.Status(new ClientVersion(1, 2, 0, null), NOW, NOW, false));

        Instant later = NOW.plusSeconds(60);
        when(clock.instant()).thenReturn(later);
        when(latestClient.fetchLatest()).thenReturn(Optional.empty());
        assertThat(service.refresh()).isFalse();
        assertThat(service.status()).isEqualTo(
                new ClientVersionService.Status(new ClientVersion(1, 2, 0, null), NOW, later, true));
    }
}
