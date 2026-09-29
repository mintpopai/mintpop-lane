package ai.mintpop.lane.config;

import ai.mintpop.lane.client.RestClientSubFetchClient;
import ai.mintpop.lane.client.RetryingSubFetchClient;
import ai.mintpop.lane.client.Sleeper;
import ai.mintpop.lane.client.SubFetchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 拉订阅用的 HTTP 客户端。超时钉死，避免管理端请求被慢源站长时间挂住。 */
@Configuration
public class SubHttpConfig {

    @Bean
    RestClient subRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        return builder.requestFactory(factory).build();
    }

    /** 拉取客户端：HTTP 实现外套重试装饰器。实现类不加 @Component，装配只在这里 */
    @Bean
    SubFetchClient subFetchClient(RestClient subRestClient) {
        return new RetryingSubFetchClient(new RestClientSubFetchClient(subRestClient), Sleeper.SYSTEM);
    }
}
