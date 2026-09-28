package ai.mintpop.lane.service;

import ai.mintpop.lane.response.FrontSubscriptionBrief;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 用户第一跳机场订阅列表：只由管理员手动触发分配或清空 */
public interface FrontSubscriptionService {

    /** 按场景负载算法整份重算该用户的列表；主用名额全满报 FRONT_CAPACITY_FULL */
    List<FrontSubscriptionBrief> allocate(Long userId);

    /** 清空该用户的列表，释放主用名额 */
    void clear(Long userId);

    /** 批量取用户列表摘要；没有列表的用户不出现在结果里 */
    Map<Long, List<FrontSubscriptionBrief>> briefsOf(Collection<Long> userIds);
}
