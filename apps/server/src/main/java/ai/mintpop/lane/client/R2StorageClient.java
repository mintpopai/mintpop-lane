package ai.mintpop.lane.client;

import ai.mintpop.lane.config.StorageProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Cloudflare R2 存储客户端：只管「写对象 + 拼公开 URL」，不懂业务。
 * SDK 异常（软件 / 网络 / 4xx 5xx）原样上抛，由服务层翻译成业务码。
 * 不标 @Component：由 StorageConfig 在 storage.r2 配置齐全时装配，未配置则容器里没有这个 bean。
 */
public class R2StorageClient {

    /** 对象键含 uuid 永不覆盖，可以放心让浏览器与 Cloudflare 边缘缓存一年 */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3Client;
    private final StorageProperties properties;

    public R2StorageClient(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /** 写入对象并返回自定义域名下的公开 URL */
    public String put(String key, String contentType, byte[] bytes) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(bytes));
        return publicBaseUrl() + "/" + key;
    }

    private String publicBaseUrl() {
        String base = properties.getPublicBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
