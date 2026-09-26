package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@link ai.mintpop.lane.client.CachingIpAsnClient} 的缓存参数。默认值未经生产实测，
 * 全部可在 {@code config/application.yml} 按需覆盖，见 {@code application.example.yml} 里的注释。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ip-asn-cache")
public class IpAsnCacheProperties {

    /** IP 的 ASN 归属一天之内基本不变；再久就该重查了（机房搬迁、IP 段转让） */
    private Duration ttl = Duration.ofHours(24);

    /** 容量上限。按「活跃用户数量级」取，几千条的 record 内存占用可忽略 */
    private int maxEntries = 5_000;
}
