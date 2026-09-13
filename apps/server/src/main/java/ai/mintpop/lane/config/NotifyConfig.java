package ai.mintpop.lane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * 通知装配：启用 @Async（通知在独立线程池执行，不阻塞管理端请求与巡检）、
 * 启用 @Scheduled（出口 IP 巡检按 EgressCheckProperties.interval 定时跑）+ 飞书 webhook 专用 RestClient。
 * 与 SubHttpConfig 分开：两者超时策略不同，也让「通知」这块可以整体拿掉而不牵连订阅拉取。
 */
@Configuration
@EnableAsync
@EnableScheduling
public class NotifyConfig {

    /** 连接/读取各 5s 短超时：通知是尽力而为，绝不许占住异步线程 */
    @Bean
    RestClient feishuRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        return builder.requestFactory(factory).build();
    }
}
