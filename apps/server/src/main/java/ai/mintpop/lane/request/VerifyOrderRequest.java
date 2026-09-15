package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 核实订单支付状态的入参 */
@Data
public class VerifyOrderRequest {

    @NotBlank(message = "订单号不能为空")
    private String orderNo;
}
