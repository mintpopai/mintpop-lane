package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建分组的入参：只给分组名与链接，订阅里的美国节点由服务端自动导入 */
@Data
public class NodeGroupCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 订阅链接（含 token），由服务端拉取并挑出美国节点导入，敏感参数不经前端往返 */
    @NotBlank
    private String subUrl;

    @Size(max = 255)
    private String remark;
}
