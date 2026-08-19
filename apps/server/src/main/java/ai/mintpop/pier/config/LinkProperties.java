package ai.mintpop.pier.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 链路的非敏感参数。
 * 用户绑定表与节点池已下沉到数据库，配置文件里只剩下这一个纯参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "pier.link")
public class LinkProperties {

    /** 下发给客户端的配置有效期（秒） */
    private long ttlSeconds = 1800;
}
