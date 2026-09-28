package ai.mintpop.lane.service;

import ai.mintpop.lane.request.AirportSubscriptionCreateRequest;
import ai.mintpop.lane.request.AirportSubscriptionRenameRequest;
import ai.mintpop.lane.response.AirportSubscriptionResponse;

import java.util.List;

public interface AdminAirportSubscriptionService {

    /** 建订阅并自动导入订阅里的美国节点，返回订阅 id；订阅里一个美国节点都没有时报 410049 */
    Long create(AirportSubscriptionCreateRequest request);

    List<AirportSubscriptionResponse> list();

    void rename(Long id, AirportSubscriptionRenameRequest request);

    /** 用订阅里存的链接重拉，自动导入其中的美国节点：已入池的更新参数，新的入库 */
    void importNodes(Long id);

    /** 删除订阅并连带删除订阅内节点；订阅内有节点被用户绑定则整体拒绝 */
    void delete(Long id);
}
