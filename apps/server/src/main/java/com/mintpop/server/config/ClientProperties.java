package com.mintpop.server.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 桌面端引导配置里本服务自有的参数。
 * issuer 与 API Resource 不在这里重复声明：它们复用资源服务器的鉴权配置，
 * 保证客户端拿到的 resource 与服务端校验的 aud 必然是同一个值。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "mintpop.client")
public class ClientProperties {

    /** Logto 中桌面端那个「原生应用」的 App ID */
    @NotBlank
    private String logtoClientId;
}
