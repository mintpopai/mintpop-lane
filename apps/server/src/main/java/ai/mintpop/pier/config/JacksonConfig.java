package ai.mintpop.pier.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 经典 Jackson 2 兼容基础设施。
 * <p>
 * Spring Boot 4.1 默认只自动装配 Jackson 3（{@code tools.jackson}）用于 HTTP 序列化，
 * 不再提供经典 Jackson 2（{@code com.fasterxml.jackson}）的 {@link ObjectMapper} bean；
 * 而 MyBatis-Plus 的 JacksonTypeHandler（{@code extra_config}/{@code egress_ips} 两个
 * JSON 列用的那个）内部硬编码依赖经典 {@link ObjectMapper}，本项目 converter 层
 * （如 ProxyNodeConverter）序列化敏感键 JSON 时同样注入的是这个经典类型，
 * 故在此显式提供一个 bean，供两者共用。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
