package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 分组改名入参。不支持改订阅链接——换链接等于建新分组 */
@Data
public class NodeGroupRenameRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 255)
    private String remark;
}
