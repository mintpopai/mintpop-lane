package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/** 兑换席位凭证的入参 */
@Data
public class CredentialExchangeRequest {

    @NotBlank(message = "会话标识不能为空")
    private String sessionId;

    /** 回调页给出的授权码，形如 authCode#state。排除出 toString，它能换出凭证 */
    @NotBlank(message = "授权码不能为空")
    @ToString.Exclude
    private String code;
}
