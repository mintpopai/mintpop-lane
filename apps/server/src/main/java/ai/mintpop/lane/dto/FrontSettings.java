package ai.mintpop.lane.dto;

import ai.mintpop.lane.enumeration.NodeRegion;

/** 第一跳三项全局设置的类型化视图，由 SystemSettingService 从键值表解析而来 */
public record FrontSettings(NodeRegion region, int airportsPerUser, int bandwidthPerUserMbps) {

    public static final int MIN_AIRPORTS_PER_USER = 1;
    public static final int MAX_AIRPORTS_PER_USER = 10;
    public static final int MIN_BANDWIDTH_PER_USER_MBPS = 1;
    public static final int MAX_BANDWIDTH_PER_USER_MBPS = 1000;

    /** 订阅的主用容量：订阅带宽 ÷ 每人带宽，向下取整 */
    public int primaryCapacity(int bandwidthMbps) {
        return bandwidthMbps / bandwidthPerUserMbps;
    }
}
