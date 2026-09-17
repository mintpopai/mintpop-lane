package ai.mintpop.lane.service;

import ai.mintpop.lane.client.R2StorageClient;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.ImageType;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.response.ImageUploadResponse;
import ai.mintpop.lane.util.ImageTypeDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * 管理端图片上传：校验（存储已配置、非空、大小、魔数判型）→ 生成对象键 → 写 R2 → 返回公开 URL。
 * 存储客户端是可选依赖：storage.r2 未配齐时容器里没有 R2StorageClient，这里按「未配置」报业务码。
 */
@Slf4j
@Service
public class ImageUploadService {

    /** 单文件上限 5MB，与 application.yaml 的 spring.servlet.multipart.max-file-size、管理端本地预检一致 */
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    /** 对象键的目录段：按 UTC 年月分目录，便于在 R2 控制台翻看 */
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Optional<R2StorageClient> storage;
    private final Clock clock;

    @Autowired
    public ImageUploadService(Optional<R2StorageClient> storage) {
        this(storage, Clock.systemUTC());
    }

    /** 测试用：固定时钟以断言对象键的年月目录 */
    ImageUploadService(Optional<R2StorageClient> storage, Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    public ImageUploadResponse upload(MultipartFile file) {
        R2StorageClient client = storage
                .orElseThrow(() -> new BizException(BizCodeEnum.IMAGE_STORAGE_NOT_CONFIGURED));
        if (file.isEmpty()) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }
        // Spring multipart 上限先拦大头，这里是防御性二次检查（上限被调大或走了别的入口时仍成立）
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(BizCodeEnum.IMAGE_TOO_LARGE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("读取上传文件失败", e);
        }
        ImageType type = ImageTypeDetector.detect(bytes)
                .orElseThrow(() -> new BizException(BizCodeEnum.IMAGE_TYPE_UNSUPPORTED));
        String key = "plans/" + YearMonth.now(clock).format(MONTH_DIR)
                + "/" + UUID.randomUUID() + "." + type.getExtension();
        try {
            return new ImageUploadResponse(client.put(key, type.getMimeType(), bytes));
        } catch (SdkException e) {
            log.warn("R2 写入失败 key={}", key, e);
            throw new BizException(BizCodeEnum.IMAGE_STORAGE_ERROR);
        }
    }
}
