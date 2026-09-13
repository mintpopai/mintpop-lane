package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FeishuBotClient;
import ai.mintpop.lane.config.NotifyProperties;
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

import java.util.LinkedHashMap;

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
}
