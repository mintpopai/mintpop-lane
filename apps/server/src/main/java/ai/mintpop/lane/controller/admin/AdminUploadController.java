package ai.mintpop.lane.controller.admin;

import ai.mintpop.lane.response.ApiResponse;
import ai.mintpop.lane.response.ImageUploadResponse;
import ai.mintpop.lane.service.ImageUploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端上传接口。整个 /api/admin/** 由 SecurityConfig 统一要求 ROLE_ADMIN，本类不再自行鉴权。
 */
@RestController
@RequestMapping("/api/admin/uploads")
public class AdminUploadController {

    private final ImageUploadService imageUploadService;

    public AdminUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    /** 上传图片到 R2，返回公开 URL；套餐主图与富文本插图共用 */
    @PostMapping("/images")
    public ApiResponse<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success(imageUploadService.upload(file));
    }
}
