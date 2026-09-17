package ai.mintpop.lane.client;

import ai.mintpop.lane.config.StorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 桩掉 S3Client，不碰网络。之前这段拼 URL 的逻辑挂在 ImageUploadServiceTest 下，但那边把
 * R2StorageClient 整个 @Mock 掉，实现下沉到这里之后就再没有测试真正跑过 publicBaseUrl()。
 */
@ExtendWith(MockitoExtension.class)
class R2StorageClientTest {

    @Mock
    private S3Client s3Client;

    private R2StorageClient client(String publicBaseUrl) {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("mintpop-lane-assets");
        properties.setPublicBaseUrl(publicBaseUrl);
        return new R2StorageClient(s3Client, properties);
    }

    @Test
    @DisplayName("public-base-url 带尾斜杠时，拼出的 URL 只有一个斜杠")
    void tripsTrailingSlash() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = client("https://assets.lane.mintpop.ai/").put(
                "plans/2026/09/x.png", "image/png", new byte[] {1});

        assertThat(url).isEqualTo("https://assets.lane.mintpop.ai/plans/2026/09/x.png");
    }

    @Test
    @DisplayName("public-base-url 不带尾斜杠时，拼出的 URL 同样只有一个斜杠")
    void keepsNoTrailingSlash() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        String url = client("https://assets.lane.mintpop.ai").put(
                "plans/2026/09/x.png", "image/png", new byte[] {1});

        assertThat(url).isEqualTo("https://assets.lane.mintpop.ai/plans/2026/09/x.png");
    }

    @Test
    @DisplayName("写入请求带上 bucket、content-type 与 Cache-Control")
    void putsWithBucketContentTypeAndCacheControl() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        client("https://assets.lane.mintpop.ai").put("plans/2026/09/x.png", "image/png", new byte[] {1});

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("mintpop-lane-assets");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    }
}
