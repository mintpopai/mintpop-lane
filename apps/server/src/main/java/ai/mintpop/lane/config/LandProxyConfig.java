package ai.mintpop.lane.config;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.client.LandProxyClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 经落地出口出站相关的生产装配。
 *
 * EgressIpVerifier 刻意不加 @Component（见其类注释），构造依赖的
 * LandProxyClientFactory 已由组件扫描装配，这里只负责把两者接起来。
 */
@Configuration
public class LandProxyConfig {

    @Bean
    public EgressIpVerifier egressIpVerifier(LandProxyClientFactory factory) {
        return EgressIpVerifier.over(factory);
    }
}
