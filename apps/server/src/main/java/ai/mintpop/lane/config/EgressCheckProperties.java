package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 落地出口 IP 巡检配置（notify.egress-check.*）。
 * 巡检本身只为发飞书告警存在：未配置飞书 webhook 时巡检不跑，见 EgressCheckService。
 */
@Data
@Component
@ConfigurationProperties(prefix = "notify.egress-check")
public class EgressCheckProperties {

    /** 巡检间隔（上一轮结束到下一轮开始）；Spring Boot 时长写法，如 1h、30m。首轮也等这么久再跑 */
    private Duration interval = Duration.ofHours(1);
}
