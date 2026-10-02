package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 手动分配第一跳的入参：按顺位排列的机场订阅 id，第 0 个为主用。内容校验放服务层，统一报 FRONT_MANUAL_INVALID */
@Data
public class FrontManualAssignRequest {

    @NotNull
    private List<Long> airportSubscriptionIds;
}
