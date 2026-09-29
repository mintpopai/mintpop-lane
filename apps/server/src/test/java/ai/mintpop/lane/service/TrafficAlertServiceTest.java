package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchResult;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.repository.AirportSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.TaskRejectedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("订阅额度与到期告警：额度跨档才推，重置后清档；剩余不足三天另推一条")
class TrafficAlertServiceTest {

    /** 测试基准时刻，配合各用例构造出「还剩几天到期」 */
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Mock private AirportSubscriptionRepository airportSubscriptionRepository;
    @Mock private NodeNotifyService nodeNotifyService;

    private TrafficAlertService service;

    @BeforeEach
    void setUp() {
        service = new TrafficAlertService(airportSubscriptionRepository, nodeNotifyService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AirportSubscriptionDto group(Integer alertedPct) {
        AirportSubscriptionDto group = new AirportSubscriptionDto();
        group.setId(1L);
        group.setName("TaiShan Net");
        group.setTrafficAlertedPct(alertedPct);
        return group;
    }

    /** total=100，used 即百分比，省去换算；到期时间留空，只走额度那条分支 */
    private SubFetchResult used(long percent) {
        return new SubFetchResult("proxies: []", null, percent, 100L, null);
    }

    /** 只带到期时间、不带额度的拉取结果，只走到期那条分支 */
    private SubFetchResult expiringIn(Duration remaining) {
        return new SubFetchResult("proxies: []", null, null, null, NOW.plus(remaining));
    }

    @Test
    @DisplayName("首次跨 80% 推一次并记下档位")
    void alertsOnFirstCrossing() {
        AirportSubscriptionDto group = group(null);

        service.checkAndNotify(group, used(85));

        verify(nodeNotifyService).notifyTrafficThreshold(group, 85);
        assertThat(group.getTrafficAlertedPct()).isEqualTo(80);
        verify(airportSubscriptionRepository).update(group);
    }

    @Test
    @DisplayName("已在 80% 档，再次拉取仍在该档不重复推")
    void staysQuietWithinSameThreshold() {
        service.checkAndNotify(group(80), used(88));

        verifyNoInteractions(nodeNotifyService);
    }

    @Test
    @DisplayName("从 80% 档跨到 95% 档再推一次")
    void alertsAgainOnHigherThreshold() {
        AirportSubscriptionDto group = group(80);

        service.checkAndNotify(group, used(96));

        verify(nodeNotifyService).notifyTrafficThreshold(group, 96);
        assertThat(group.getTrafficAlertedPct()).isEqualTo(95);
    }

    @Test
    @DisplayName("用量重置回落后清档，下次再跨 80% 能重新推")
    void resetsThresholdAfterUsageDrops() {
        AirportSubscriptionDto group = group(95);

        service.checkAndNotify(group, used(5));
        assertThat(group.getTrafficAlertedPct()).isNull();
        verifyNoInteractions(nodeNotifyService);

        service.checkAndNotify(group, used(82));
        verify(nodeNotifyService).notifyTrafficThreshold(group, 82);
    }

    @Test
    @DisplayName("机场没返回额度头时整段跳过，不推也不改档")
    void skipsWhenQuotaUnknown() {
        AirportSubscriptionDto group = group(null);

        service.checkAndNotify(group, new SubFetchResult("proxies: []", null, null, null, null));

        verifyNoInteractions(nodeNotifyService);
        verifyNoInteractions(airportSubscriptionRepository);
    }

    @Test
    @DisplayName("用量低于最低档且此前没推过：不推也不落库")
    void staysUntouchedWhenBelowLowestThresholdWithNoPriorAlert() {
        service.checkAndNotify(group(null), used(42));

        verifyNoInteractions(nodeNotifyService);
        verifyNoInteractions(airportSubscriptionRepository); // 关键：连一次 update 都不该有——最常见的运行态不能白白落库
    }

    @Test
    @DisplayName("通知抛异常不影响已完成的改库")
    void notifyFailureDoesNotBreakPersistence() {
        AirportSubscriptionDto group = group(null);
        doThrow(new TaskRejectedException("执行器已关闭"))
                .when(nodeNotifyService).notifyTrafficThreshold(any(), anyInt());

        assertThatCode(() -> service.checkAndNotify(group, used(85))).doesNotThrowAnyException();
        verify(airportSubscriptionRepository).update(group);
    }

    @Test
    @DisplayName("剩余不足三天推一条到期告警，全程不写库——不去重就没有要持久化的状态")
    void alertsWhenExpiringWithinThreeDays() {
        AirportSubscriptionDto group = group(null);

        service.checkAndNotify(group, expiringIn(Duration.ofDays(2)));

        verify(nodeNotifyService).notifySubscriptionExpiring(group, NOW.plus(Duration.ofDays(2)),
                Duration.ofDays(2));
        verifyNoInteractions(airportSubscriptionRepository);
    }

    @Test
    @DisplayName("剩余超过三天不推：三天是告警窗口，不是「有到期时间就推」")
    void staysQuietWhenExpiryIsFarAway() {
        service.checkAndNotify(group(null), expiringIn(Duration.ofDays(10)));

        verify(nodeNotifyService, never()).notifySubscriptionExpiring(any(), any(), any());
    }

    @Test
    @DisplayName("已过期同样推：那是仍在持续的故障，不是可以翻篇的历史事件")
    void alertsWhenAlreadyExpired() {
        AirportSubscriptionDto group = group(null);

        service.checkAndNotify(group, expiringIn(Duration.ofDays(-1)));

        verify(nodeNotifyService).notifySubscriptionExpiring(group, NOW.minus(Duration.ofDays(1)),
                Duration.ofDays(-1));
    }

    @Test
    @DisplayName("机场没返回到期时间时整段跳过，不推也不写库")
    void skipsExpiryCheckWhenExpiresAtUnknown() {
        service.checkAndNotify(group(null), used(42));

        verify(nodeNotifyService, never()).notifySubscriptionExpiring(any(), any(), any());
    }

    @Test
    @DisplayName("额度与到期是两条独立分支：用量没跨档也照样能推到期告警")
    void expiryAlertIsIndependentOfUsageThreshold() {
        AirportSubscriptionDto group = group(null);

        service.checkAndNotify(group, new SubFetchResult("proxies: []", null, 10L, 100L,
                NOW.plus(Duration.ofHours(5))));

        verifyNoInteractions(airportSubscriptionRepository);
        verify(nodeNotifyService, never()).notifyTrafficThreshold(any(), anyInt());
        verify(nodeNotifyService).notifySubscriptionExpiring(group, NOW.plus(Duration.ofHours(5)),
                Duration.ofHours(5));
    }

    @Test
    @DisplayName("到期通知抛异常不影响额度那条分支已完成的改库")
    void expiryNotifyFailureDoesNotBreakUsagePersistence() {
        AirportSubscriptionDto group = group(null);
        doThrow(new TaskRejectedException("执行器已关闭"))
                .when(nodeNotifyService).notifySubscriptionExpiring(any(), any(), any());

        assertThatCode(() -> service.checkAndNotify(group,
                new SubFetchResult("proxies: []", null, 85L, 100L, NOW.plus(Duration.ofDays(1)))))
                .doesNotThrowAnyException();
        verify(airportSubscriptionRepository).update(group);
        assertThat(group.getTrafficAlertedPct()).isEqualTo(80);
    }

    /** 可手动拨动时间的时钟 */
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private SubFetchResult expiringAt(Instant expiresAt) {
        return new SubFetchResult("proxies: []", null, null, null, expiresAt);
    }

    @Test
    @DisplayName("同一订阅 5 分钟后再刷新不重复推，25 小时后再推一次")
    void suppressesExpiryAlertWithin24Hours() {
        MutableClock clock = new MutableClock(NOW);
        TrafficAlertService svc = new TrafficAlertService(airportSubscriptionRepository, nodeNotifyService, clock);
        AirportSubscriptionDto group = group(null);
        Instant expiresAt = NOW.plus(Duration.ofDays(2));

        svc.checkAndNotify(group, expiringAt(expiresAt));
        clock.advance(Duration.ofMinutes(5));
        svc.checkAndNotify(group, expiringAt(expiresAt));
        verify(nodeNotifyService, org.mockito.Mockito.times(1)).notifySubscriptionExpiring(any(), any(), any());

        clock.advance(Duration.ofHours(25));
        svc.checkAndNotify(group, expiringAt(expiresAt));
        verify(nodeNotifyService, org.mockito.Mockito.times(2)).notifySubscriptionExpiring(any(), any(), any());
    }

    @Test
    @DisplayName("不同订阅的到期抑制互相独立")
    void expirySuppressionIsPerSubscription() {
        MutableClock clock = new MutableClock(NOW);
        TrafficAlertService svc = new TrafficAlertService(airportSubscriptionRepository, nodeNotifyService, clock);
        AirportSubscriptionDto first = group(null);
        AirportSubscriptionDto second = group(null);
        second.setId(2L);
        SubFetchResult result = expiringAt(NOW.plus(Duration.ofDays(1)));

        svc.checkAndNotify(first, result);
        svc.checkAndNotify(second, result);

        verify(nodeNotifyService, org.mockito.Mockito.times(2)).notifySubscriptionExpiring(any(), any(), any());
    }

    @Test
    @DisplayName("续期离开窗口后清记录，再次进入窗口立即能推")
    void renewalClearsSuppression() {
        MutableClock clock = new MutableClock(NOW);
        TrafficAlertService svc = new TrafficAlertService(airportSubscriptionRepository, nodeNotifyService, clock);
        AirportSubscriptionDto group = group(null);

        svc.checkAndNotify(group, expiringAt(NOW.plus(Duration.ofDays(1))));
        clock.advance(Duration.ofMinutes(5));
        svc.checkAndNotify(group, expiringAt(NOW.plus(Duration.ofDays(30))));
        clock.advance(Duration.ofMinutes(5));
        svc.checkAndNotify(group, expiringAt(NOW.plus(Duration.ofDays(1))));

        verify(nodeNotifyService, org.mockito.Mockito.times(2)).notifySubscriptionExpiring(any(), any(), any());
    }
}
