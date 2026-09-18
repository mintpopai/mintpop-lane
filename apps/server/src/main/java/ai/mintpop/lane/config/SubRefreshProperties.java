package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 订阅定时刷新配置（sub-refresh.*）。
 * 定时对齐已有节点的参数、故障域与分组额度，见 SubRefreshService；
 * 订阅里增删的节点只推飞书告知，不受本配置影响（增删告警与是否配置飞书 webhook 无关，未配则整体静默）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sub-refresh")
public class SubRefreshProperties {

    /** 刷新间隔（上一轮结束到下一轮开始）；Spring Boot 时长写法，如 1h、24h。首轮也等这么久再跑 */
    private Duration interval = Duration.ofHours(24);
}
