package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建分组并导入勾选节点的入参 */
@Data
public class NodeGroupCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 订阅链接（含 token），服务端会重新拉取一次再按勾选过滤，敏感参数不经前端往返 */
    @NotBlank
    private String subUrl;

    /** 勾选的订阅原始节点名 */
    @NotEmpty
    private List<@NotBlank String> selectedNames;

    @Size(max = 255)
    private String remark;
}
