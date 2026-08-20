package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/** 桌面端票据兑换入参。两个值都是一次性秘密，排除出 toString 防日志外泄。 */
@Data
public class DesktopExchangeRequest {

    @NotBlank(message = "ticket 不能为空")
    @ToString.Exclude
    private String ticket;

    @NotBlank(message = "verifier 不能为空")
    @ToString.Exclude
    private String verifier;
}
