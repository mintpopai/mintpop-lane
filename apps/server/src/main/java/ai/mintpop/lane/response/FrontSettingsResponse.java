package ai.mintpop.lane.response;

import ai.mintpop.lane.enumeration.NodeRegion;

import java.util.List;

/** 全局配置页的读视图：当前值 + 地区下拉选项 */
public record FrontSettingsResponse(
        NodeRegion region,
        List<RegionOption> regionOptions,
        int airportsPerUser,
        int bandwidthPerUserMbps
) {
    public record RegionOption(NodeRegion value, String label) {
    }
}
