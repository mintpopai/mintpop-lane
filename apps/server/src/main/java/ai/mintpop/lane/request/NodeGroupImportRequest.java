package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** 重新拉取后的增量导入入参 */
@Data
public class NodeGroupImportRequest {

    /** 勾选的订阅原始节点名；已入池的名字表示「更新其参数」 */
    @NotEmpty
    private List<@NotBlank String> selectedNames;
}
