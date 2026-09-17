package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cloudflare R2 对象存储配置（storage.r2.*）：凭据来自 jar 外 config/application.yml，不入库不进仓库。
 * 整段可选：五项齐全才装配存储客户端（见 StorageConfig），否则图片上传接口返回「图片存储未配置」。
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage.r2")
public class StorageProperties {

    /** Cloudflare 账户 ID，用于拼 S3 兼容端点 */
    private String accountId;
    /** R2 API Token 生成的 Access Key ID（敏感） */
    private String accessKeyId;
    /** R2 API Token 生成的 Secret Access Key（敏感） */
    private String secretAccessKey;
    /** 桶名（本项目约定 mintpop-lane-assets） */
    private String bucket;
    /** 桶绑定的自定义域名，形如 https://assets.lane.mintpop.ai；拼公开 URL 时去掉末尾斜杠 */
    private String publicBaseUrl;

    /** 五项都有值才算配置完整 */
    public boolean isConfigured() {
        return hasText(accountId) && hasText(accessKeyId) && hasText(secretAccessKey)
                && hasText(bucket) && hasText(publicBaseUrl);
    }

    /** R2 的 S3 兼容端点：https://<accountId>.r2.cloudflarestorage.com */
    public String endpoint() {
        return "https://" + accountId + ".r2.cloudflarestorage.com";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
