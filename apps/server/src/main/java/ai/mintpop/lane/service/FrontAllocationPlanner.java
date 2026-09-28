package ai.mintpop.lane.service;

/**
 * 第一跳按机场订阅分配的纯函数：场景负载与分配算法（spec 第四节）。与数据库无关，可单独单测。
 */
public final class FrontAllocationPlanner {

    /** 每个用户按多少带宽计主用容量（Mbps），写死 */
    public static final int BANDWIDTH_PER_USER_MBPS = 20;

    /** 每个用户的列表里最多几家机场，写死 */
    public static final int MAX_AIRPORTS_PER_USER = 3;

    private FrontAllocationPlanner() {
    }

    /** 订阅的主用容量：带宽 / 20 向下取整 */
    public static int primaryCapacity(int bandwidthMbps) {
        return bandwidthMbps / BANDWIDTH_PER_USER_MBPS;
    }
}
