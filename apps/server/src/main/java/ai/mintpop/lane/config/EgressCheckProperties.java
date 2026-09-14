package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 落地出口 IP 巡检配置（egress-check.*）。
 * 巡检是独立的节点维护任务：定时探测实际出口，与登记不一致就回填库（时区同步重解析），见 EgressCheckService。
 * 飞书通知只是回填后的附带动作，未配置 webhook 巡检照常跑，所以本配置与 notify.* 平级、不挂在它下面。
 */
@Data
@Component
@ConfigurationProperties(prefix = "egress-check")
public class EgressCheckProperties {

    /** 巡检间隔（上一轮结束到下一轮开始）；Spring Boot 时长写法，如 10m、1h。首轮也等这么久再跑 */
    private Duration interval = Duration.ofMinutes(10);
}
