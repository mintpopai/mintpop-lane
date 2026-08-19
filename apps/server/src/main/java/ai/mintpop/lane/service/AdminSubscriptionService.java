package ai.mintpop.lane.service;

import ai.mintpop.lane.request.SubscriptionSaveRequest;
import ai.mintpop.lane.response.AdminSubscriptionResponse;

import java.util.List;

public interface AdminSubscriptionService {

    /** 某用户的全部订阅（含已过期），按 id 升序 */
    List<AdminSubscriptionResponse> listByUser(Long userId);

    /** 给用户新建一条订阅（一次购买 = 一条记录），返回新 id */
    Long create(Long userId, SubscriptionSaveRequest request);

    /** 更新订阅（延期、换凭据、改名）。credential 留空沿用原值 */
    void update(Long id, SubscriptionSaveRequest request);

    void delete(Long id);
}
