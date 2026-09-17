package ai.mintpop.lane.util;

import ai.mintpop.lane.enumeration.ImageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {

    /** 造一个至少 12 字节的样本：前缀是魔数，后面用 0 补齐 */
    private static byte[] sample(byte... magic) {
        byte[] bytes = new byte[Math.max(12, magic.length)];
        System.arraycopy(magic, 0, bytes, 0, magic.length);
        return bytes;
    }

    private static byte[] ascii(String text) {
        return sample(text.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("四种支持的图片各自按魔数认得出来")
    void detectsSupportedTypes() {
        assertThat(ImageTypeDetector.detect(sample((byte) 0xFF, (byte) 0xD8, (byte) 0xFF)))
                .contains(ImageType.JPEG);
        assertThat(ImageTypeDetector.detect(
                sample((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                        (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A)))
                .contains(ImageType.PNG);
        assertThat(ImageTypeDetector.detect(ascii("GIF87a"))).contains(ImageType.GIF);
        assertThat(ImageTypeDetector.detect(ascii("GIF89a"))).contains(ImageType.GIF);
    }

    @Test
    @DisplayName("WebP 要 RIFF 开头且第 8–11 字节是 WEBP，只有 RIFF 不算")
    void detectsWebpOnlyWithBothMarkers() {
        byte[] webp = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, webp, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, webp, 8, 4);
        assertThat(ImageTypeDetector.detect(webp)).contains(ImageType.WEBP);

        // 只有 RIFF（比如 wav 文件）不能被当成图片
        assertThat(ImageTypeDetector.detect(ascii("RIFF"))).isEmpty();
    }

    @Test
    @DisplayName("SVG 与 HTML 一律判不出型：可执行内容不许以图片名义写进公开桶")
    void rejectsExecutableContent() {
        assertThat(ImageTypeDetector.detect(ascii("<svg xmlns=\"http://www.w3.org/2000/svg\">"))).isEmpty();
        assertThat(ImageTypeDetector.detect(ascii("<!DOCTYPE html><script>alert(1)</script>"))).isEmpty();
    }

    @Test
    @DisplayName("null、空数组、不足 12 字节都判不出型")
    void rejectsTooShortInput() {
        assertThat(ImageTypeDetector.detect(null)).isEmpty();
        assertThat(ImageTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(ImageTypeDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})).isEmpty();
    }
}
