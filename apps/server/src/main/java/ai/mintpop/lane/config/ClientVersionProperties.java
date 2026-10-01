package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 桌面端强制更新的版本来源（client-version.*），见 ClientVersionService。
 * <p>
 * 最新版不在服务端配置，而是读桌面端发版写出的更新清单：发布脚本先传完安装包、确认公网可下，
 * 最后才写这份清单，所以服务端看到新版本号时安装包一定已经能下——不会把用户拦住却无包可升。
 * 客户端自动更新读的也是同一份，两边对「最新版」的认识不会错开。
 */
@Data
@Component
@ConfigurationProperties(prefix = "client-version")
public class ClientVersionProperties {

    /** 桌面端更新清单地址（Tauri updater 的 latest.json），只取其中的 version */
    private String manifestUrl = "https://dl.mintpop.ai/lane/latest.json";

    /** 重拉间隔，与清单在 CDN 上的 max-age=60 对齐；启动时立即拉一次 */
    private Duration refreshInterval = Duration.ofMinutes(1);
}
