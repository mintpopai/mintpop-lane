package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.FeishuCardTemplate;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeNotifyServiceTest {

    @Mock
    private FeishuBotClient feishuBotClient;
    @Mock
    private UserRepository userRepository;
    private NotifyProperties properties;
    private NodeNotifyService service;

    @BeforeEach
    void setUp() {
        properties = new NotifyProperties();
        properties.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/test");
        service = new NodeNotifyService(properties, feishuBotClient, userRepository);
    }

    private ProxyNodeDto land(String egressIp, String timezone) {
        ProxyNodeDto dto = new ProxyNodeDto();
        dto.setId(7L);
        dto.setName("LAND-1");
        dto.setRole(NodeRole.LAND);
        dto.setEgressIp(egressIp);
        dto.setEgressTimezone(timezone);
        return dto;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, String> sentFields(FeishuCardTemplate template) {
        ArgumentCaptor<LinkedHashMap<String, String>> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(feishuBotClient).sendCard(eq(template), eq("MintPop Lane 落地出口 IP 已变更"), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("管理端修改：绿色卡片，字段依次为节点、原/新出口 IP、原/新时区、来源、绑定用户数")
    void adminChangeSendsGreenCard() {
        when(userRepository.countByLandNodeId(7L)).thenReturn(3L);

        service.notifyEgressIpChanged(land("203.0.113.9", "Asia/Tokyo"), "203.0.113.7", "America/Los_Angeles",
                EgressIpChangeSource.ADMIN);

        assertThat(sentFields(FeishuCardTemplate.GREEN)).containsExactly(
                entry("节点", "LAND-1（ID 7）"),
                entry("原出口 IP", "203.0.113.7"),
                entry("新出口 IP", "203.0.113.9"),
                entry("原时区", "America/Los_Angeles"),
                entry("新时区", "Asia/Tokyo"),
                entry("来源", "管理端修改"),
                entry("绑定用户数", "3"));
    }

    @Test
    @DisplayName("定时巡检自动回填：橙色卡片，来源标「定时巡检自动回填」")
    void egressCheckChangeSendsOrangeCard() {
        when(userRepository.countByLandNodeId(7L)).thenReturn(0L);

        service.notifyEgressIpChanged(land("203.0.113.9", "Asia/Tokyo"), "203.0.113.7", "Asia/Tokyo",
                EgressIpChangeSource.EGRESS_CHECK);

        assertThat(sentFields(FeishuCardTemplate.ORANGE))
                .containsEntry("来源", "定时巡检自动回填")
                .containsEntry("新时区", "Asia/Tokyo");
    }

    @Test
    @DisplayName("原值未登记时显示「未登记」，不显示 null")
    void unregisteredValuesRendered() {
        service.notifyEgressIpChanged(land("203.0.113.9", "Asia/Tokyo"), null, null, EgressIpChangeSource.ADMIN);

        assertThat(sentFields(FeishuCardTemplate.GREEN))
                .containsEntry("原出口 IP", "未登记")
                .containsEntry("原时区", "未登记");
    }

    @Test
    @DisplayName("未配置 webhook：整体静默，不碰客户端也不查库")
    void silentWhenNotConfigured() {
        properties.setWebhookUrl(null);

        service.notifyEgressIpChanged(land("203.0.113.9", null), "203.0.113.7", null, EgressIpChangeSource.ADMIN);

        verifyNoInteractions(feishuBotClient, userRepository);
    }

    @Test
    @DisplayName("客户端抛任何异常：只记日志，不向调用方冒泡")
    void swallowsClientFailure() {
        doThrow(new IllegalStateException("飞书机器人返回异常"))
                .when(feishuBotClient).sendCard(any(), anyString(), any());

        assertThatCode(() -> service.notifyEgressIpChanged(land("203.0.113.9", null), "203.0.113.7", null,
                EgressIpChangeSource.ADMIN)).doesNotThrowAnyException();
    }

    private AirportSubscriptionDto group(long id, String name) {
        AirportSubscriptionDto group = new AirportSubscriptionDto();
        group.setId(id);
        group.setName(name);
        return group;
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, String> sentFields(FeishuCardTemplate template, String title) {
        ArgumentCaptor<LinkedHashMap<String, String>> captor = ArgumentCaptor.forClass(LinkedHashMap.class);
        verify(feishuBotClient).sendCard(eq(template), eq(title), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("订阅即将到期：橙色卡片（与额度告警同一张），订阅/到期时间/剩余依次展示")
    void expiringSoonSendsOrangeCard() {
        service.notifySubscriptionExpiring(group(3L, "A 家"), Instant.parse("2026-09-20T00:00:00Z"),
                Duration.ofHours(50));

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 订阅即将到期")).containsExactly(
                entry("订阅", "A 家（ID 3）"),
                entry("到期时间", "2026-09-20T00:00:00Z"),
                entry("剩余", "2 天 2 小时"));
    }

    @Test
    @DisplayName("订阅已过期：标题与剩余都说「已过期」，不显示负的小时数")
    void expiredSaysExpired() {
        service.notifySubscriptionExpiring(group(3L, "A 家"), Instant.parse("2026-09-17T00:00:00Z"),
                Duration.ofHours(-24));

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 订阅已过期"))
                .containsEntry("剩余", "已过期");
    }

    @Test
    @DisplayName("订阅到期：未配置 webhook 整体静默")
    void expiringSilentWhenNotConfigured() {
        properties.setWebhookUrl(null);

        service.notifySubscriptionExpiring(group(3L, "A 家"), Instant.parse("2026-09-20T00:00:00Z"),
                Duration.ofHours(5));

        verifyNoInteractions(feishuBotClient);
    }

    @Test
    @DisplayName("订阅到期：客户端抛异常只记日志，不向调用方冒泡")
    void expiringSwallowsClientFailure() {
        doThrow(new IllegalStateException("飞书机器人返回异常"))
                .when(feishuBotClient).sendCard(any(), anyString(), any());

        assertThatCode(() -> service.notifySubscriptionExpiring(group(3L, "A 家"),
                Instant.parse("2026-09-20T00:00:00Z"), Duration.ofHours(5))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("故障域成功率告警：橙色卡片，用户/故障域/成功率/样本量依次展示")
    void failureDomainDegradedSendsOrangeCard() {
        service.notifyFailureDomainDegraded(1L, "u1@test.example", "jp.tsdns.top", 0.5, 100L);

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 故障域成功率告警")).containsExactly(
                entry("用户", "u1@test.example"),
                entry("故障域", "jp.tsdns.top"),
                entry("成功率", "50.0%"),
                entry("样本量", "100"));
    }

    @Test
    @DisplayName("故障域成功率告警：email 为 null（查不到用户）显示「用户 #id」而不是 null，且照常推送")
    void failureDomainDegradedShowsUserIdWhenEmailIsNull() {
        service.notifyFailureDomainDegraded(7L, null, "jp.tsdns.top", 0.5, 100L);

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 故障域成功率告警"))
                .containsEntry("用户", "用户 #7");
    }

    @Test
    @DisplayName("故障域成功率告警：未配置 webhook 整体静默")
    void failureDomainDegradedSilentWhenNotConfigured() {
        properties.setWebhookUrl(null);

        service.notifyFailureDomainDegraded(1L, "u1@test.example", "jp.tsdns.top", 0.5, 100L);

        verifyNoInteractions(feishuBotClient);
    }

    @Test
    @DisplayName("故障域成功率告警：客户端抛异常只记日志，不向调用方冒泡")
    void failureDomainDegradedSwallowsClientFailure() {
        doThrow(new IllegalStateException("飞书机器人返回异常"))
                .when(feishuBotClient).sendCard(any(), anyString(), any());

        assertThatCode(() -> service.notifyFailureDomainDegraded(1L, "u1@test.example", "jp.tsdns.top", 0.5, 100L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("运营商成功率告警：橙色卡片，用户/故障域/运营商/ASN/成功率/样本量依次展示")
    void asnDegradedSendsOrangeCard() {
        service.notifyAsnDegraded(1L, "u1@test.example", "jp.tsdns.top", "AS4134", "China Telecom", 0.4, 100L);

        // 展示名与 ASN 两行都在：名字让人一眼认出是哪家，ASN 是判定与去重真正用的那个键，
        // 只给名字则同一家改了文案后对不上号，只给 ASN 则没人看得出 AS4134 是谁
        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 运营商成功率告警")).containsExactly(
                entry("用户", "u1@test.example"),
                entry("故障域", "jp.tsdns.top"),
                entry("运营商", "China Telecom"),
                entry("ASN", "AS4134"),
                entry("成功率", "40.0%"),
                entry("样本量", "100"));
    }

    @Test
    @DisplayName("运营商成功率告警：asn_org 里没有展示名时「运营商」退回 ASN，不显示 null")
    void asnDegradedFallsBackToAsnWhenOrgNameIsNull() {
        service.notifyAsnDegraded(1L, "u1@test.example", "jp.tsdns.top", "AS4134", null, 0.4, 100L);

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 运营商成功率告警"))
                .containsEntry("运营商", "AS4134")
                .containsEntry("ASN", "AS4134");
    }

    @Test
    @DisplayName("运营商成功率告警：ASN 与展示名都没有（调用方本不该这么调）显示「未知」而不是 null")
    void asnDegradedShowsUnknownWhenBothAsnAndOrgNameAreNull() {
        // 运营商级判定只在 asn 非空时才发起（反查失败的行会被跳过），这里守的是兜底：
        // 万一哪天调用点改错，卡片上出现的是「未知」，不是字面量 null
        service.notifyAsnDegraded(1L, "u1@test.example", "jp.tsdns.top", null, null, 0.4, 100L);

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 运营商成功率告警"))
                .containsEntry("运营商", "未知")
                .containsEntry("ASN", "未知");
    }

    @Test
    @DisplayName("运营商成功率告警：email 为 null（查不到用户）显示「用户 #id」而不是 null，且照常推送")
    void asnDegradedShowsUserIdWhenEmailIsNull() {
        service.notifyAsnDegraded(7L, null, "jp.tsdns.top", "AS4134", "China Telecom", 0.4, 100L);

        assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 运营商成功率告警"))
                .containsEntry("用户", "用户 #7");
    }

    @Test
    @DisplayName("运营商成功率告警：未配置 webhook 整体静默")
    void asnDegradedSilentWhenNotConfigured() {
        properties.setWebhookUrl(null);

        service.notifyAsnDegraded(1L, "u1@test.example", "jp.tsdns.top", "AS4134", "China Telecom", 0.4, 100L);

        verifyNoInteractions(feishuBotClient);
    }

    @Test
    @DisplayName("运营商成功率告警：客户端抛异常只记日志，不向调用方冒泡")
    void asnDegradedSwallowsClientFailure() {
        doThrow(new IllegalStateException("飞书机器人返回异常"))
                .when(feishuBotClient).sendCard(any(), anyString(), any());

        assertThatCode(() -> service.notifyAsnDegraded(1L, "u1@test.example", "jp.tsdns.top", "AS4134",
                "China Telecom", 0.4, 100L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("成功率百分比按 Locale.ROOT 格式化，不随运行环境地区设置漂成逗号小数点")
    void degradedRateFormatsWithRootLocale() {
        Locale original = Locale.getDefault();
        // 德语区的小数点是逗号：String.format 不钉 Locale.ROOT 就会输出 "50,0%"
        Locale.setDefault(Locale.GERMANY);
        try {
            service.notifyFailureDomainDegraded(1L, "u1@test.example", "jp.tsdns.top", 0.5, 100L);

            assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 故障域成功率告警"))
                    .containsEntry("成功率", "50.0%");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("额度告警：橙色卡片；GB 数按 Locale.ROOT 格式化，不随运行环境地区设置漂成逗号小数点")
    void trafficThresholdFormatsBytesWithRootLocale() {
        Locale original = Locale.getDefault();
        // 德语区的小数点是逗号：String.format 不钉 Locale.ROOT 就会输出 "1,50 GB"
        Locale.setDefault(Locale.GERMANY);
        try {
            AirportSubscriptionDto group = group(3L, "A 家");
            group.setUsedBytes(1_610_612_736L);  // 1.5 GB
            group.setTotalBytes(3_221_225_472L); // 3 GB

            service.notifyTrafficThreshold(group, 50);

            assertThat(sentFields(FeishuCardTemplate.ORANGE, "MintPop Lane 订阅额度告警"))
                    .containsEntry("已用 / 总额", "1.50 GB / 3.00 GB");
        } finally {
            Locale.setDefault(original);
        }
    }
}
