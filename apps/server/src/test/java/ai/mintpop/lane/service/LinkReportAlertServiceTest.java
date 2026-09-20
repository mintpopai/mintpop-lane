package ai.mintpop.lane.service;

import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.repository.LinkAlertStateRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.repository.LinkReportRepository.DomainIspAggregate;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.support.DatabaseFixtures;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 成功率跌破阈值告警：先看样本量门槛、再算比值；去重状态落 {@code link_alert_state}（真库）。
 * <p>
 * 去重状态用真实 MySQL、通知用 mock 断言推送次数——这是 {@link #alertStateSurvivesRestart()}
 * 有判别力的前提：重新 {@code new} 出来的 Service 实例不共享任何内存字段，
 * 只有落库的状态能扛得住这次「重启」，内存 Map 方案在这里必然变红。
 */
class LinkReportAlertServiceTest extends MysqlTestBase {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private static final String DOMAIN = "jp.tsdns.top";
    private static final String ISP = "CTC";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LinkReportRepository linkReportRepository;
    @Autowired
    private LinkAlertStateRepository alertStateRepository;
    @Autowired
    private ProxyNodeRepository nodeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private DatabaseFixtures fixtures;
    private Long userId;
    private NodeNotifyService notifyService;
    private LinkReportProperties properties;

    @BeforeEach
    void setUp() {
        fixtures = new DatabaseFixtures(jdbc, nodeRepository, userRepository, subscriptionRepository);
        fixtures.clearAll();
        userId = fixtures.createUser("u1", null, null);

        notifyService = mock(NodeNotifyService.class);
        properties = new LinkReportProperties(); // 默认阈值 0.80、最小样本 20，与 spec 一致
    }

    /** 每次都 new 一个新实例，模拟「重启」不共享任何内存态；去重状态全部靠 alertStateRepository 落库读回 */
    private LinkReportAlertService newService() {
        return new LinkReportAlertService(linkReportRepository, alertStateRepository, notifyService, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 真实落一条 link_report 窗口，供 checkAll 相关测试驱动全库聚合查询 */
    private void insertWindow(Long userId, String domain, String isp, Instant windowStart, int samples, int alive) {
        LinkReport report = new LinkReport();
        report.setUserId(userId);
        report.setFailureDomain(domain);
        report.setWindowStart(windowStart);
        report.setSamples(samples);
        report.setAliveCount(alive);
        report.setNoSampleCount(0);
        report.setFailovers(0);
        report.setIsp(isp);
        linkReportRepository.upsertWindow(report);
    }

    /** 100 个样本、50 个存活 → 成功率 50%，跌破默认阈值 0.80 */
    private DomainIspAggregate degraded() {
        return new DomainIspAggregate(DOMAIN, ISP, 100, 50, 0);
    }

    /** 100 个样本、95 个存活 → 成功率 95%，高于默认阈值 0.80 */
    private DomainIspAggregate healthy() {
        return new DomainIspAggregate(DOMAIN, ISP, 100, 95, 0);
    }

    @Test
    @DisplayName("成功率跌破阈值且样本足够时告警，故障域级与运营商级各推一次")
    void alertsWhenRateBelowThresholdWithEnoughSamples() {
        newService().checkAndNotify(userId, List.of(degraded()));

        verify(notifyService).notifyFailureDomainDegraded(DOMAIN, 0.5, 100L);
        verify(notifyService).notifyIspDegraded(DOMAIN, ISP, 0.5, 100L);
    }

    @Test
    @DisplayName("样本不足一律不告警，哪怕成功率是 0")
    void staysSilentWhenSamplesBelowMinimum() {
        // samples=5 alive=0：成功率 0%，比阈值低得多，但样本量（5）低于 alertMinSamples（20）。
        // 若实现漏掉「先看样本量」这道门槛，0/5=0 < 0.80 一样会触发告警，这条测试就会变红
        newService().checkAndNotify(userId, List.of(new DomainIspAggregate(DOMAIN, ISP, 5, 0, 0)));

        verifyNoInteractions(notifyService);
    }

    @Test
    @DisplayName("samples 为 0 时不抛 ArithmeticException（“不告警”那半由 staysSilentWhenSamplesBelowMinimum 守）")
    void zeroSamplesDoesNotThrowArithmeticException() {
        // 本实现用 (double) aliveCount / samples：0.0/0 在 Java 里是 NaN，不是抛异常，
        // 且 NaN < threshold 恒为 false，所以"不告警"这半对当前实现天然成立——真正守住
        // "样本不足就不该告警"这条业务规则的是 staysSilentWhenSamplesBelowMinimum（已用破坏性
        // 自查验证过它的判别力，去掉门槛会让它变红，而这条不会）。
        // 这条测试单独守的是"不抛 ArithmeticException"：万一将来有人把除法改成 long/long 的
        // 整数除法，除数为 0 时会真的抛出，这条测试会因此变红
        assertThatCode(() -> newService().checkAndNotify(userId,
                List.of(new DomainIspAggregate(DOMAIN, ISP, 0, 0, 0))))
                .doesNotThrowAnyException();

        verifyNoInteractions(notifyService);
    }

    @Test
    @DisplayName("同一故障域持续劣化只推一次，恢复后再劣化会重新推")
    void alertIsDedupedUntilRecovered() {
        LinkReportAlertService service = newService();

        service.checkAndNotify(userId, List.of(degraded()));
        service.checkAndNotify(userId, List.of(degraded()));
        service.checkAndNotify(userId, List.of(degraded()));
        verify(notifyService, times(1)).notifyFailureDomainDegraded(DOMAIN, 0.5, 100L);
        verify(notifyService, times(1)).notifyIspDegraded(DOMAIN, ISP, 0.5, 100L);

        service.checkAndNotify(userId, List.of(healthy())); // 恢复正常：清掉已告警状态，不推
        service.checkAndNotify(userId, List.of(degraded())); // 再次劣化：应重新推一次

        verify(notifyService, times(2)).notifyFailureDomainDegraded(DOMAIN, 0.5, 100L);
        verify(notifyService, times(2)).notifyIspDegraded(DOMAIN, ISP, 0.5, 100L);
    }

    @Test
    @DisplayName("重启（重新构造 Service）后同一劣化故障域不重推——去重状态落库而非内存")
    void alertStateSurvivesRestart() {
        LinkReportAlertService first = newService();
        first.checkAndNotify(userId, List.of(degraded()));
        verify(notifyService, times(1)).notifyFailureDomainDegraded(DOMAIN, 0.5, 100L);
        verify(notifyService, times(1)).notifyIspDegraded(DOMAIN, ISP, 0.5, 100L);

        // 模拟重启：全新实例，不复用 first 的任何字段，只共享同一个落库的 alertStateRepository
        LinkReportAlertService restarted = newService();
        restarted.checkAndNotify(userId, List.of(degraded()));

        verify(notifyService, times(1)).notifyFailureDomainDegraded(DOMAIN, 0.5, 100L);
        verify(notifyService, times(1)).notifyIspDegraded(DOMAIN, ISP, 0.5, 100L);
    }

    @Test
    @DisplayName("ASN 反查失败（isp 为 null）跳过运营商级判定，只推故障域级，不会与故障域级共用的空串键位冲突")
    void unresolvedIspAggregateSkipsIspLevelToAvoidKeyCollisionWithDomainLevel() {
        // 陷阱：故障域级判定的去重键固定是 (domain, "")；若反查失败的运营商级判定也落到
        // 同一个 ""，两者会共用同一行去重状态——先跑的一个把 alerted 置 1，后跑的看到已告警
        // 直接判去重，导致其中一种通知被永久吞掉。这条用例最初就是这样红的：
        // notifyIspDegraded(DOMAIN, null, ...) 被断言但从未被调用，因为它与故障域级共用了同一行
        LinkReportAlertService service = newService();
        DomainIspAggregate unresolved = new DomainIspAggregate(DOMAIN, null, 100, 50, 0);

        assertThatCode(() -> service.checkAndNotify(userId, List.of(unresolved))).doesNotThrowAnyException();
        service.checkAndNotify(userId, List.of(unresolved)); // 第二轮应被去重，不重推

        verify(notifyService, times(1)).notifyFailureDomainDegraded(DOMAIN, 0.5, 100L);
        verify(notifyService, never()).notifyIspDegraded(anyString(), any(), anyDouble(), anyLong());
    }

    @Test
    @DisplayName("通知抛异常不影响已落库的去重状态，不会导致重复推送")
    void notifyFailureDoesNotBreakPersistedState() {
        doThrow(new TaskRejectedException("执行器已关闭"))
                .when(notifyService).notifyFailureDomainDegraded(anyString(), anyDouble(), anyLong());

        LinkReportAlertService service = newService();
        assertThatCode(() -> service.checkAndNotify(userId, List.of(degraded()))).doesNotThrowAnyException();

        // 去重状态先落库再通知，通知失败不该让状态丢失——第二轮不会因为“没记住已经推过”而重新调用
        service.checkAndNotify(userId, List.of(degraded()));
        verify(notifyService, times(1)).notifyFailureDomainDegraded(anyString(), anyDouble(), anyLong());
    }

    @Test
    @DisplayName("故障域级判定跨运营商求和：单个运营商各自不达标，但域内合计成功率仍可能不同于其中任意一个运营商")
    void domainLevelAggregatesAcrossIsps() {
        // CTC 100 中 40 存活（40%），CUCC 100 中 90 存活（90%）：域内合计 200 中 130 存活 = 65%，
        // 跌破阈值 0.80，但两个运营商级判定各自独立：CTC 应推，CUCC 不该推
        DomainIspAggregate ctc = new DomainIspAggregate(DOMAIN, "CTC", 100, 40, 0);
        DomainIspAggregate cucc = new DomainIspAggregate(DOMAIN, "CUCC", 100, 90, 0);

        newService().checkAndNotify(userId, List.of(ctc, cucc));

        verify(notifyService).notifyFailureDomainDegraded(DOMAIN, 0.65, 200L);
        verify(notifyService).notifyIspDegraded(DOMAIN, "CTC", 0.4, 100L);
        verify(notifyService, times(0)).notifyIspDegraded(DOMAIN, "CUCC", 0.9, 100L);
    }

    @Test
    @DisplayName("checkAll 定时扫描：多个不同用户的劣化各自独立推送，不是只看到第一个用户")
    void checkAllNotifiesEachDegradedUserIndependently() {
        Long userA = userId;
        Long userB = fixtures.createUser("u2", null, null);
        Instant windowStart = NOW.minus(Duration.ofMinutes(5)); // 落在默认 15 分钟回看窗口内

        insertWindow(userA, "a.tsdns.top", ISP, windowStart, 100, 50); // 50%，跌破阈值
        insertWindow(userB, "b.tsdns.top", ISP, windowStart, 100, 40); // 40%，跌破阈值

        newService().checkAll();

        verify(notifyService).notifyFailureDomainDegraded("a.tsdns.top", 0.5, 100L);
        verify(notifyService).notifyIspDegraded("a.tsdns.top", ISP, 0.5, 100L);
        verify(notifyService).notifyFailureDomainDegraded("b.tsdns.top", 0.4, 100L);
        verify(notifyService).notifyIspDegraded("b.tsdns.top", ISP, 0.4, 100L);
    }

    @Test
    @DisplayName("checkAll 只看回看窗口内的数据，窗口外的旧数据不参与本轮判定")
    void checkAllIgnoresWindowsOutsideLookback() {
        Instant tooOld = NOW.minus(properties.getAlertLookback()).minusSeconds(1);
        insertWindow(userId, DOMAIN, ISP, tooOld, 100, 50);

        newService().checkAll();

        verifyNoInteractions(notifyService);
    }
}
