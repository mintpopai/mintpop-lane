package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 新建/更新机场的入参，更新时全量覆盖 */
@Data
public class AirportSaveRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 机场地址（官网或用户中心），可空 */
    @Size(max = 255)
    private String websiteUrl;

    @Size(max = 255)
    private String remark;

    /** 是否主用机场：false 时其订阅只当备用，不会被分配为主用 */
    @NotNull
    private Boolean primaryEnabled;
}
