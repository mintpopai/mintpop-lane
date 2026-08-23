package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/**
 * 给用户分配订阅的入参。只能从现有套餐里选：名称/时长/价格一律取自所选套餐，
 * 止期由服务端按套餐时长推算，agent 类型取自所选套餐，均不在入参里出现。
 */
@Data
public class SubscriptionCreateRequest {

    @NotNull(message = "套餐不能为空")
    private Long planId;

    /** 归属企业 id；留空表示个人订阅 */
    private Long enterpriseId;

    /** 服务起期；留空表示从分配当下开始 */
    private Instant startsAt;

    /** 席位凭据。排除出 toString，避免凭据随日志外泄 */
    @ToString.Exclude
    private String credential;

    @Size(max = 255, message = "备注最长 255 个字符")
    private String remark;
}
