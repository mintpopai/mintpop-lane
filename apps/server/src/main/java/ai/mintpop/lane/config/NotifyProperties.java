package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 飞书通知配置（notify.feishu.*）：webhook 地址与签名密钥来自 jar 外 config/application.yml，
 * 不入库不进仓库。webhook-url 为空时通知功能整体静默关闭。
 */
@Data
@Component
@ConfigurationProperties(prefix = "notify.feishu")
public class NotifyProperties {

    /** 群自定义机器人 webhook 地址（敏感，泄露可被刷消息） */
    private String webhookUrl;

    /** 机器人签名校验密钥（飞书机器人安全设置开启「签名校验」后提供；为空则不签名） */
    private String secret;

    /** 通知功能是否已配置启用 */
    public boolean isConfigured() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
