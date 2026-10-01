package ai.mintpop.lane.config;

import ai.mintpop.lane.security.ClientVersionInterceptor;
import ai.mintpop.lane.service.ClientVersionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 桌面端强制更新：拉清单用的 HTTP 客户端 + 版本闸门的挂载范围。
 */
@Configuration
public class ClientVersionConfig implements WebMvcConfigurer {

    /**
     * 只挂桌面端专用的接口。/api/me 控制台网页也在调、不带版本头，挂上去会把控制台打挂；
     * 桌面端那一路由心跳兜底，最迟一分钟内照样被拦下。
     * 新增桌面端专用接口时要补进这里。
     */
    static final String[] DESKTOP_PATHS = {
            "/api/link/**",
            "/api/subscriptions/*/device/**",
            "/api/auth/desktop/exchange",
    };

    private final ClientVersionService clientVersionService;

    public ClientVersionConfig(ClientVersionService clientVersionService) {
        this.clientVersionService = clientVersionService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ClientVersionInterceptor(clientVersionService)).addPathPatterns(DESKTOP_PATHS);
    }

    /**
     * 短超时：拉不到就沿用上一次的值，不许一次挂住的请求拖住调度线程。
     * 必须是 static：本类构造要注入 ClientVersionService，而它经 LatestClientVersionClient 又要这个 RestClient，
     * 实例方法会让创建这个 bean 先要本类实例，形成循环依赖、上下文起不来。
     */
    @Bean
    static RestClient clientVersionRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(5_000);
        return builder.requestFactory(factory).build();
    }
}
