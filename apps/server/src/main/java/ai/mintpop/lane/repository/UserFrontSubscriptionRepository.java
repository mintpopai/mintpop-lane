package ai.mintpop.lane.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 用户第一跳机场订阅列表的读写口。列表一律按顺位（position 升序）返回 */
public interface UserFrontSubscriptionRepository {

    List<Long> findSubscriptionIdsByUserId(Long userId);

    /** 批量取；没有列表的用户不出现在结果里，调用方按 getOrDefault(id, List.of()) 取值 */
    Map<Long, List<Long>> findSubscriptionIdsByUserIds(Collection<Long> userIds);

    /** 全部用户的列表，分配算法算场景负载用 */
    Map<Long, List<Long>> findAllGroupedByUser();

    /** 整份替换：传入的订阅 id 按顺位排列，下标即 position */
    void replaceForUser(Long userId, List<Long> airportSubscriptionIdsInOrder);

    void deleteByUserId(Long userId);

    boolean existsByAirportSubscriptionId(Long airportSubscriptionId);

    /** 各订阅当主用（position = 0）的人数；没有人的订阅不出现在结果里 */
    Map<Long, Long> countPrimaryByAirportSubscription();
}
