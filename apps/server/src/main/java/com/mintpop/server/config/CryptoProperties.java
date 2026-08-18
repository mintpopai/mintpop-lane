package com.mintpop.server.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 加密配置。密钥由部署机的环境变量注入，不进库、不进镜像、不入库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mintpop.crypto")
public class CryptoProperties {

    /**
     * Base64 编码的 32 字节（256 位）AES 密钥。全仓唯一持有这把主密钥的地方，
     * 排除出 toString——它一旦随日志/异常泄露，后果是全库密文（凭据与节点密码）
     * 全部作废，比泄露单条凭据严重一个数量级。
     */
    @ToString.Exclude
    private String key;
}
