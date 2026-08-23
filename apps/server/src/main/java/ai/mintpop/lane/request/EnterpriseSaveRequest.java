package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 新建/更新企业的入参，更新时全量覆盖 */
@Data
public class EnterpriseSaveRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /**
     * 企业域名，形如 acme.com。大小写都收，服务端统一转小写入库，
     * 故校验正则两种大小写都放行。
     */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)+$")
    private String domain;

    /** 本企业支持的 agent 类型，至少选一个 */
    @NotEmpty
    private List<@NotNull AgentType> agentTypes;

    /** 启用状态：false 表示停用但保留 */
    @NotNull
    private Boolean enabled;

    @Size(max = 255)
    private String remark;
}
