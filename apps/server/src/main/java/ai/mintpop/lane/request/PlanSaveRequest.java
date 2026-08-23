package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    /** 上架状态：false 表示停用但保留 */
    @NotNull
    private Boolean enabled;

    @Size(max = 255)
    private String remark;
}
