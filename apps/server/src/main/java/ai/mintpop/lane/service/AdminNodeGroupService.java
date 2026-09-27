package ai.mintpop.lane.service;

import ai.mintpop.lane.request.NodeGroupCreateRequest;
import ai.mintpop.lane.request.NodeGroupRenameRequest;
import ai.mintpop.lane.response.NodeGroupResponse;

import java.util.List;

public interface AdminNodeGroupService {

    /** 建分组并自动导入订阅里的美国节点，返回分组 id；订阅里一个美国节点都没有时报 410049 */
    Long create(NodeGroupCreateRequest request);

    List<NodeGroupResponse> list();

    void rename(Long id, NodeGroupRenameRequest request);

    /** 用分组里存的链接重拉，自动导入其中的美国节点：已入池的更新参数，新的入库 */
    void importNodes(Long id);

    /** 删除分组并连带删除组内节点；组内有节点被用户绑定则整体拒绝 */
    void delete(Long id);
}
