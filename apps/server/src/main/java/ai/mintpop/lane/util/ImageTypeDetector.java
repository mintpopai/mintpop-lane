package ai.mintpop.lane.util;

import ai.mintpop.lane.enumeration.ImageType;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * 按文件头魔数判定图片类型，只看前 12 字节。判不出四种之一就是不支持——
 * 把 HTML / SVG 这类可执行内容以图片名义写进公开桶的口子从这里堵住。
 */
public final class ImageTypeDetector {

    /** 需要检查的最小字节数：WebP 的 WEBP 标记在 8–11 字节 */
    private static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87A = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89A = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ImageTypeDetector() {
    }

    public static Optional<ImageType> detect(byte[] bytes) {
        if (bytes == null || bytes.length < HEADER_LENGTH) {
            return Optional.empty();
        }
        if (startsWith(bytes, 0, JPEG)) {
            return Optional.of(ImageType.JPEG);
        }
        if (startsWith(bytes, 0, PNG)) {
            return Optional.of(ImageType.PNG);
        }
        if (startsWith(bytes, 0, GIF87A) || startsWith(bytes, 0, GIF89A)) {
            return Optional.of(ImageType.GIF);
        }
        if (startsWith(bytes, 0, RIFF) && startsWith(bytes, 8, WEBP)) {
            return Optional.of(ImageType.WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, byte[] magic) {
        return Arrays.equals(bytes, offset, offset + magic.length, magic, 0, magic.length);
    }
}
