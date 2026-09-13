package ai.mintpop.lane.config;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.client.LandProxyClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 经落地出口出站相关的生产装配。
 *
 * 探测实现单独成 bean：签发时的 EgressIpVerifier 与管理端的节点连通性检测共用它，
 * 测试也只需替换这一个 bean 就能让两条路径都不真正出网。
 * EgressIpVerifier 刻意不加 @Component（见其类注释），由这里装配。
 */
@Configuration
public class LandProxyConfig {

    @Bean
    public EgressIpVerifier.EgressProbe egressProbe(LandProxyClientFactory factory) {
        return EgressIpVerifier.probeOver(factory);
    }

    @Bean
    public EgressIpVerifier egressIpVerifier(EgressIpVerifier.EgressProbe probe) {
        return new EgressIpVerifier(probe);
    }
}
