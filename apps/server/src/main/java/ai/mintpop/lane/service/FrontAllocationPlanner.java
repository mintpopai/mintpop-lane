package ai.mintpop.lane.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 第一跳按机场订阅分配的纯函数：场景负载与分配算法（spec 第四节）。与数据库无关，可单独单测。
 * <p>
 * 一个故障场景就是「挂掉的机场集合 F」。场景 F 下每个用户落到自己列表里第一个机场不在 F 中的那一项；
 * 给新用户选第 i 位时，他面对的场景是「前 i 位的机场都挂了」，在这个场景下挑
 * 「(负载 + 1) / 带宽」最小的订阅。只看集合、不看顺序，列表更短的用户也被自然计入。
 */
public final class FrontAllocationPlanner {

    /** 每个用户按多少带宽计主用容量（Mbps），写死 */
    public static final int BANDWIDTH_PER_USER_MBPS = 20;

    /** 每个用户的列表里最多几家机场，写死 */
    public static final int MAX_AIRPORTS_PER_USER = 3;

    private FrontAllocationPlanner() {
    }

    /** 列表里的一项：哪家机场的哪个订阅 */
    public record Slot(long airportId, long airportSubscriptionId) {
    }

    /** 一个用户的有序列表，slots 按顺位排列（第 0 位主用） */
    public record Assignment(long userId, List<Slot> slots) {
    }

    /** 可分配的订阅（调用方已滤掉没有可用美国节点的订阅） */
    public record Candidate(long airportSubscriptionId, long airportId, int bandwidthMbps) {

        public int primaryCapacity() {
            return FrontAllocationPlanner.primaryCapacity(bandwidthMbps);
        }
    }

    /** 订阅的主用容量：带宽 / 20 向下取整 */
    public static int primaryCapacity(int bandwidthMbps) {
        return bandwidthMbps / BANDWIDTH_PER_USER_MBPS;
    }

    /** 场景 F（这些机场都挂了）下，所有用户最终落在各订阅上的人数；key 为订阅 id */
    public static Map<Long, Integer> scenarioLoad(Set<Long> failedAirports, List<Assignment> assignments) {
        Map<Long, Integer> load = new HashMap<>();
        for (Assignment assignment : assignments) {
            for (Slot slot : assignment.slots()) {
                if (!failedAirports.contains(slot.airportId())) {
                    load.merge(slot.airportSubscriptionId(), 1, Integer::sum);
                    break;
                }
            }
            // 列表里的机场全在 F 中：该用户在此场景下无处可去，不计入
        }
        return load;
    }

    /**
     * 给一个用户算出有序列表。others 必须排除该用户自己（重算时他的旧列表不能挡住自己）。
     * 返回空列表表示主用名额全满；可用机场不足 M 家时列表短于 M。
     */
    public static List<Slot> plan(List<Assignment> others, List<Candidate> candidates) {
        List<Slot> order = new ArrayList<>();
        Set<Long> failed = new HashSet<>();

        for (int position = 0; position < MAX_AIRPORTS_PER_USER; position++) {
            Map<Long, Integer> load = scenarioLoad(failed, others);
            boolean primary = position == 0;
            Candidate best = candidates.stream()
                    .filter(c -> !failed.contains(c.airportId()))
                    // 只有主用占容量：此时 failed 为空，load 就是主用人数
                    .filter(c -> !primary || load.getOrDefault(c.airportSubscriptionId(), 0) < c.primaryCapacity())
                    .min(Comparator
                            .comparingDouble((Candidate c) ->
                                    (load.getOrDefault(c.airportSubscriptionId(), 0) + 1.0) / c.bandwidthMbps())
                            .thenComparingLong(Candidate::airportSubscriptionId))
                    .orElse(null);
            if (best == null) {
                // 第 0 位选不出 = 主用名额全满，由调用方报错；之后的位选不出 = 可用机场不足，列表变短
                break;
            }
            order.add(new Slot(best.airportId(), best.airportSubscriptionId()));
            failed.add(best.airportId());
        }
        return order;
    }
}
