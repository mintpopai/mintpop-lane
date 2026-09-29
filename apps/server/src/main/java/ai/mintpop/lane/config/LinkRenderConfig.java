package ai.mintpop.lane.config;

import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.service.InMemorySubscriptionRenderCache;
import ai.mintpop.lane.service.SubscriptionRenderCache;
import ai.mintpop.lane.service.SystemSettingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 链路渲染缓存的装配点：实现类不加 @Component，将来换 Redis 只改这里 */
@Configuration
public class LinkRenderConfig {

    @Bean
    public SubscriptionRenderCache subscriptionRenderCache(ProxyNodeRepository nodeRepository,
                                                           FrontTuningProperties frontTuningProperties,
                                                           SystemSettingService systemSettingService) {
        return new InMemorySubscriptionRenderCache(nodeRepository, frontTuningProperties, systemSettingService);
    }
}
