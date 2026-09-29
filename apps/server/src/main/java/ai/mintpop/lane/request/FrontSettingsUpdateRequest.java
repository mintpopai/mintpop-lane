package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.NodeRegion;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 全局配置保存入参。范围校验放服务层，统一报 SETTING_INVALID，不靠 Bean Validation 的通用 110001 */
@Data
public class FrontSettingsUpdateRequest {

    @NotNull
    private NodeRegion region;

    @NotNull
    private Integer airportsPerUser;

    @NotNull
    private Integer bandwidthPerUserMbps;
}
