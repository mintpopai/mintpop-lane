package ai.mintpop.lane.service;

import ai.mintpop.lane.client.R2StorageClient;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.response.ImageUploadResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    /** 固定到 2026-09 ，好断言对象键里的年月目录 */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-16T08:00:00Z"), ZoneOffset.UTC);

    @Mock
    private R2StorageClient storage;

    private ImageUploadService service(Optional<R2StorageClient> client) {
        return new ImageUploadService(client, FIXED_CLOCK);
    }

    /** 一张最小的合法 PNG：只要前 8 字节是 PNG 魔数、总长 ≥ 12 字节即可通过判型 */
    private MockMultipartFile pngFile() {
        byte[] bytes = new byte[16];
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        return new MockMultipartFile("file", "a.png", "image/png", bytes);
    }

    @Test
    @DisplayName("存储未配置时报 410044，不去碰存储")
    void rejectsWhenStorageNotConfigured() {
        assertThatThrownBy(() -> service(Optional.empty()).upload(pngFile()))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.IMAGE_STORAGE_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("空文件报 110001")
    void rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> service(Optional.of(storage)).upload(empty))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.PARAM_INVALID);
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("超过 5 MB 报 410045")
    void rejectsTooLargeFile() {
        byte[] big = new byte[(int) ImageUploadService.MAX_BYTES + 1];
        big[0] = (byte) 0x89;
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", big);

        assertThatThrownBy(() -> service(Optional.of(storage)).upload(file))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.IMAGE_TOO_LARGE);
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("判不出图片类型报 410046：改了扩展名的 HTML 也进不来")
    void rejectsUnsupportedType() {
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "a.png", "image/png", "<!DOCTYPE html><script>x</script>".getBytes());

        assertThatThrownBy(() -> service(Optional.of(storage)).upload(disguised))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.IMAGE_TYPE_UNSUPPORTED);
        verify(storage, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("成功时对象键按 UTC 年月分目录、扩展名随判出的类型，返回存储给的公开 URL")
    void uploadsWithMonthlyKey() {
        when(storage.put(any(), eq("image/png"), any()))
                .thenReturn("https://assets.lane.mintpop.ai/plans/2026/09/x.png");

        ImageUploadResponse response = service(Optional.of(storage)).upload(pngFile());

        assertThat(response.url()).isEqualTo("https://assets.lane.mintpop.ai/plans/2026/09/x.png");
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).put(key.capture(), eq("image/png"), any());
        assertThat(key.getValue()).matches("plans/2026/09/[0-9a-f-]{36}\\.png");
    }

    @Test
    @DisplayName("存储写入失败翻译成 410047，不把 SDK 异常透给调用方")
    void translatesStorageFailure() {
        when(storage.put(any(), any(), any())).thenThrow(SdkException.create("网络不通", null));

        assertThatThrownBy(() -> service(Optional.of(storage)).upload(pngFile()))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.IMAGE_STORAGE_ERROR);
    }
}
