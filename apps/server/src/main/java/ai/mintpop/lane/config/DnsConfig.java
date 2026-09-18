package ai.mintpop.lane.config;

import ai.mintpop.lane.client.EcsDnsClient;
import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.JndiFailureDomainResolver;
import ai.mintpop.lane.client.RestClientEcsDnsClient;
import ai.mintpop.lane.client.RestClientIpAsnClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** DNS 相关客户端的装配点：实现类不加 @Component，依赖由这里注入，测试可整体替换。 */
@Configuration
public class DnsConfig {

    @Bean
    public FailureDomainResolver failureDomainResolver() {
        return new JndiFailureDomainResolver(JndiFailureDomainResolver.jndiLookup());
    }

    /** 查 dns.google 用的 HTTP 客户端。短超时：尽调是同步请求，不能被慢查询拖住管理端接口 */
    @Bean
    RestClient dnsRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        return builder.requestFactory(factory).build();
    }

    @Bean
    public EcsDnsClient ecsDnsClient(RestClient dnsRestClient) {
        return new RestClientEcsDnsClient(dnsRestClient);
    }

    /** 复用 GeoIpConfig 的 geoIpRestClient：与 IpTimezoneClient 同一目标服务（ipwho.is），不必另起一份 RestClient 配置 */
    @Bean
    public IpAsnClient ipAsnClient(RestClient geoIpRestClient) {
        return new RestClientIpAsnClient(geoIpRestClient);
    }
}
