package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.AgentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/** 新建或更新订阅的入参。更新时 credential 留空表示沿用原值（页面上看不到原凭据）。 */
@Data
public class SubscriptionSaveRequest {

    @NotNull(message = "agent 类型不能为空")
    private AgentType agentType;

    @NotBlank(message = "套餐名不能为空")
    @Size(max = 64, message = "套餐名最长 64 个字符")
    private String name;

    @NotNull(message = "起期不能为空")
    private LocalDateTime startsAt;

    @NotNull(message = "止期不能为空")
    private LocalDateTime endsAt;

    /** 席位凭据。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private String credential;

    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;
}
