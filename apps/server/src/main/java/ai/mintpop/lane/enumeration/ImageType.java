package ai.mintpop.lane.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 允许上传的图片类型：写入 R2 时用 mimeType 当 Content-Type，用 extension 拼对象键。
 * 类型由文件头魔数判定（见 ImageTypeDetector），不信任浏览器传的 Content-Type 与文件扩展名。
 */
@Getter
@AllArgsConstructor
public enum ImageType {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif");

    private final String mimeType;
    private final String extension;
}
