package ai.mintpop.lane.config;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.client.JndiFailureDomainResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** DNS 相关客户端的装配点：实现类不加 @Component，依赖由这里注入，测试可整体替换。 */
@Configuration
public class DnsConfig {

    @Bean
    public FailureDomainResolver failureDomainResolver() {
        return new JndiFailureDomainResolver(JndiFailureDomainResolver.jndiLookup());
    }
}
