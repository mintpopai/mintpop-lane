package ai.mintpop.lane.config;

import ai.mintpop.lane.client.CachingIpAsnClient;
import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.JndiFailureDomainResolver;
import ai.mintpop.lane.client.RestClientIpAsnClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** DNS 相关客户端的装配点：实现类不加 @Component，依赖由这里注入，测试可整体替换。 */
@Configuration
public class DnsConfig {

    @Bean
    public FailureDomainResolver failureDomainResolver() {
        return new JndiFailureDomainResolver(JndiFailureDomainResolver.jndiLookup());
    }

    /**
     * 复用 GeoIpConfig 的 geoIpRestClient：与 IpTimezoneClient 同一目标服务（ipwho.is），不必另起一份 RestClient 配置。
     * <p>
     * 外面套一层 {@link CachingIpAsnClient}：三期的链路上报把 ASN 反查放进了心跳的同步路径，
     * 同一个来源 IP 每 5 分钟就会被重复问一次，不缓存会把免费的 ipwho.is 打到限流（理由详见该类）。
     * 缓存装在**接口这一层**。
     * TTL 与容量由 {@link IpAsnCacheProperties} 装配，可在 {@code config/application.yml} 覆盖。
     */
    @Bean
    public IpAsnClient ipAsnClient(RestClient geoIpRestClient, Clock clock, IpAsnCacheProperties props) {
        return new CachingIpAsnClient(new RestClientIpAsnClient(geoIpRestClient), clock, props.getTtl(), props.getMaxEntries());
    }
}
