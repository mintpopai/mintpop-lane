package ai.mintpop.lane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 出口 IP → 时区的 GeoIP 查询用 HTTP 客户端。短超时：查询失败有明确的降级/中止路径，不许挂住请求或巡检。 */
@Configuration
public class GeoIpConfig {

    @Bean
    RestClient geoIpRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        return builder.requestFactory(factory).build();
    }
}
