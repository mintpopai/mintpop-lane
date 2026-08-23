package ai.mintpop.lane.service;

import ai.mintpop.lane.request.SubscriptionCreateRequest;
import ai.mintpop.lane.request.SubscriptionUpdateRequest;
import ai.mintpop.lane.response.AdminSubscriptionResponse;

import java.util.List;

public interface AdminSubscriptionService {

    /** 某用户的全部订阅（含已过期），按 id 升序 */
    List<AdminSubscriptionResponse> listByUser(Long userId);

    /**
     * 给用户分配一条订阅（一次分配 = 一条记录），返回新 id。
     * 只能从现有且上架的套餐里选；套餐信息落快照，止期按套餐时长推算，并生成唯一分配号。
     */
    Long create(Long userId, SubscriptionCreateRequest request);

    /** 更新订阅（顺延起期、换凭据、改备注）。套餐不可换；credential 留空沿用原值 */
    void update(Long id, SubscriptionUpdateRequest request);

    void delete(Long id);
}
