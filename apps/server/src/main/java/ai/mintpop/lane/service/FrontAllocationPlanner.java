package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.FrontSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    private FrontAllocationPlanner() {
    }

    /** 列表里的一项：哪家机场的哪个订阅 */
    public record Slot(long airportId, long airportSubscriptionId) {
    }

    /** 一个用户的有序列表，slots 按顺位排列（第 0 位主用） */
    public record Assignment(long userId, List<Slot> slots) {
    }

    /** 可分配的订阅（调用方已滤掉当前地区没有节点的订阅） */
    public record Candidate(long airportSubscriptionId, long airportId, int bandwidthMbps) {

        public int primaryCapacity(int bandwidthPerUserMbps) {
            return FrontAllocationPlanner.primaryCapacity(bandwidthMbps, bandwidthPerUserMbps);
        }
    }

    /** 订阅的主用容量：订阅带宽 / 每人带宽（全局配置）向下取整 */
    public static int primaryCapacity(int bandwidthMbps, int bandwidthPerUserMbps) {
        return bandwidthMbps / bandwidthPerUserMbps;
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
     * 返回空列表表示主用名额全满；可用机场不足 settings.airportsPerUser() 家时列表短于它。
     */
    public static List<Slot> plan(List<Assignment> others, List<Candidate> candidates, FrontSettings settings) {
        List<Slot> order = new ArrayList<>();
        Set<Long> failed = new HashSet<>();

        for (int position = 0; position < settings.airportsPerUser(); position++) {
            Map<Long, Integer> load = scenarioLoad(failed, others);
            boolean primary = position == 0;
            Candidate best = candidates.stream()
                    .filter(c -> !failed.contains(c.airportId()))
                    // 只有主用占容量：此时 failed 为空，load 就是主用人数
                    .filter(c -> !primary || load.getOrDefault(c.airportSubscriptionId(), 0) < c.primaryCapacity(settings.bandwidthPerUserMbps()))
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

    /**
     * 全体重算：按给定顺序（调用方按用户 id 升序传入）从零开始逐个分配，前面的结果作为后面的 others。
     * 从零重排而不是在旧列表上增量，结果只取决于输入，同样的输入得到同样的输出。
     * 某用户得到空列表表示主用名额已耗尽，调用方应整体中止；这里不抛异常，让调用方拿到完整画面。
     * 复杂度 O(M·N²)，1 万用户约 3 亿次简单比较，秒级。
     */
    public static LinkedHashMap<Long, List<Slot>> planAll(List<Long> userIdsInOrder, List<Candidate> candidates, FrontSettings settings) {
        LinkedHashMap<Long, List<Slot>> result = new LinkedHashMap<>();
        List<Assignment> assigned = new ArrayList<>();
        for (Long userId : userIdsInOrder) {
            List<Slot> slots = plan(assigned, candidates, settings);
            result.put(userId, slots);
            if (!slots.isEmpty()) {
                assigned.add(new Assignment(userId, slots));
            }
        }
        return result;
    }
}
