package ai.mintpop.lane.config;

import ai.mintpop.lane.client.R2StorageClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * R2 存储装配：storage.r2 五项都非空白才装配，判据是 StorageProperties.isConfigured()，
 * 缺任一项或任一项为空串整个类都不生效、容器里没有这两个 bean，ImageUploadService 据此判定
 * 「图片存储未配置」。
 * 条件挂在类上而不是各 bean 上：@ConditionalOnBean 只在自动配置里可靠，用户配置类别用。
 */
@Configuration
@Conditional(StorageConfiguredCondition.class)
public class StorageConfig {

    /**
     * 按 Cloudflare 官方 Java SDK 示例配置：
     * - region 固定 auto（SDK 必填、R2 不用）；
     * - path-style 访问；
     * - 关闭分块编码——SDK 对 putObject 默认 chunked transfer，R2 会报签名不匹配（HTTP 403）；
     * - 校验和只在必需时算：SDK 2.30 起默认给每个请求加 CRC32 校验和，第三方 S3 兼容存储上按需即可。
     */
    @Bean
    public S3Client r2S3Client(StorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKeyId(), properties.getSecretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    @Bean
    public R2StorageClient r2StorageClient(S3Client r2S3Client, StorageProperties properties) {
        return new R2StorageClient(r2S3Client, properties);
    }
}
