package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 订阅定时刷新配置（sub-refresh.*），默认 5 分钟。
 * 定时把各订阅的节点集合整体对齐到机场当前给出的节点并更新额度，见 SubRefreshService；
 * 拉取失败只标记订阅并推飞书，不动节点。
 */
@Data
@Component
@ConfigurationProperties(prefix = "sub-refresh")
public class SubRefreshProperties {

    /** 刷新间隔（上一轮结束到下一轮开始）；Spring Boot 时长写法，如 5m、1h。首轮也等这么久再跑 */
    private Duration interval = Duration.ofMinutes(5);
}
