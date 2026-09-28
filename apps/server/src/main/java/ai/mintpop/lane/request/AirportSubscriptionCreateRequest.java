package ai.mintpop.lane.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建订阅的入参：只给订阅名与链接，订阅里的美国节点由服务端自动导入 */
@Data
public class AirportSubscriptionCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 所属机场，创建后不可改 */
    @NotNull
    private Long airportId;

    /** 购买该订阅所用的机场账号 */
    @NotBlank
    @Size(max = 128)
    private String account;

    /** 订阅总带宽（Mbps），创建后不可改 */
    @NotNull
    @Min(1)
    @Max(100000)
    private Integer bandwidthMbps;

    /** 订阅链接（含 token），由服务端拉取并挑出美国节点导入，敏感参数不经前端往返 */
    @NotBlank
    private String subUrl;

    @Size(max = 255)
    private String remark;
}
