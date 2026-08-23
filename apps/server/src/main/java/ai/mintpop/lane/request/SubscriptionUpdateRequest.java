package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/**
 * 更新订阅的入参。套餐、时长与名称在分配后不可改（要换套餐就删了重新分配）；
 * 止期由服务端按快照时长随起期重算。credential 留空表示沿用原值（页面上看不到原凭据）。
 */
@Data
public class SubscriptionUpdateRequest {

    @NotNull(message = "起期不能为空")
    private Instant startsAt;

    /** 归属企业 id；留空表示个人订阅。与凭据不同，这里留空就是清除归属 */
    private Long enterpriseId;

    /** 席位凭据。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private String credential;

    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;
}
