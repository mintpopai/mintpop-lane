package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 订阅预览入参 */
@Data
public class SubPreviewRequest {

    /** 订阅链接（含 token） */
    @NotBlank
    private String subUrl;
}
