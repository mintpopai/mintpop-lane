package ai.mintpop.lane.service;

import ai.mintpop.lane.response.FrontSubscriptionBrief;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 用户第一跳机场订阅列表：只由管理员手动触发分配（自动或手动指定）或清空 */
public interface FrontSubscriptionService {

    /** 按场景负载算法整份重算该用户的列表；主用名额全满报 FRONT_CAPACITY_FULL */
    List<FrontSubscriptionBrief> allocate(Long userId);

    /**
     * 管理员手动指定整份列表（第 0 个主用，其余备用），标记为手动。
     * 每家机场最多一项、主用必须是主用机场、订阅在当前地区要有节点，否则报 FRONT_MANUAL_INVALID；
     * 主用名额满了照样允许（手动分配就是越过算法的口子），超额由机场订阅页的名额显示提示
     */
    List<FrontSubscriptionBrief> assignManually(Long userId, List<Long> airportSubscriptionIdsInOrder);

    /** 清空该用户的列表，释放主用名额 */
    void clear(Long userId);

    /** 批量取用户列表摘要；没有列表的用户不出现在结果里 */
    Map<Long, List<FrontSubscriptionBrief>> briefsOf(Collection<Long> userIds);

    /** 列表为手动指定的用户 id */
    Set<Long> manualUserIds();
}
