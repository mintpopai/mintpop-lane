package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 下单入参：只选套餐，其余（席位账号、企业归属）由管理员开通时填 */
@Data
public class OrderCreateRequest {

    @NotNull(message = "套餐不能为空")
    private Long planId;
}
