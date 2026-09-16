package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** 新建/更新套餐的入参，更新时全量覆盖 */
@Data
public class PlanSaveRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    /** 本套餐面向的 agent 类型 */
    @NotNull
    private AgentType agentType;

    /** 套餐时长（天），正整数 */
    @NotNull
    @Min(1)
    private Integer durationDays;

    @NotNull
    @DecimalMin("0")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal price;

    @NotNull
    private Currency currency;

    /** 面向用户的短描述，控制台购买卡片的副标题，可空 */
    @Size(max = 255)
    private String description;

    /** 套餐图公开 URL，可空；只收 https，不许把 http 图混进 https 页面 */
    @Size(max = 512)
    @Pattern(regexp = "^https://.+")
    private String imageUrl;

    /** 套餐详情富文本，服务端会按白名单净化后入库，可空 */
    private String detail;

    /** 上架状态：false 表示停用但保留 */
    @NotNull
    private Boolean enabled;

    @Size(max = 255)
    private String remark;
}
