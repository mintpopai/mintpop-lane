package ai.mintpop.lane.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyPropertiesTest {

    @Test
    @DisplayName("webhook-url 有值即视为已配置；为空/空白则关闭")
    void configuredOnlyWhenWebhookUrlPresent() {
        NotifyProperties p = new NotifyProperties();
        assertThat(p.isConfigured()).isFalse();

        p.setWebhookUrl("   ");
        assertThat(p.isConfigured()).isFalse();

        p.setWebhookUrl("https://open.feishu.cn/open-apis/bot/v2/hook/xxx");
        assertThat(p.isConfigured()).isTrue();
    }
}
