package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 订阅尽调入参：候选机场的试用订阅链接 */
@Data
public class SubAuditRequest {

    /** 候选机场的订阅链接（含 token） */
    @NotBlank
    private String subUrl;
}
