package com.mintpop.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 加密配置。密钥由部署机的环境变量注入，不进库、不进镜像、不入库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mintpop.crypto")
public class CryptoProperties {

    /** Base64 编码的 32 字节（256 位）AES 密钥 */
    private String key;
}
