package ai.mintpop.lane.service;

import ai.mintpop.lane.request.NodeGroupCreateRequest;
import ai.mintpop.lane.request.NodeGroupImportRequest;
import ai.mintpop.lane.request.NodeGroupRenameRequest;
import ai.mintpop.lane.response.NodeGroupResponse;
import ai.mintpop.lane.response.SubPreviewNodeResponse;

import java.util.List;

public interface AdminNodeGroupService {

    /** 拉取并解析订阅，返回全部条目，不落库 */
    List<SubPreviewNodeResponse> preview(String subUrl);

    /** 建分组并导入勾选节点（服务端重新拉取一次订阅），返回分组 id */
    Long create(NodeGroupCreateRequest request);

    List<NodeGroupResponse> list();

    void rename(Long id, NodeGroupRenameRequest request);

    /** 用分组里存的链接重拉，返回条目并标出已入池的 */
    List<SubPreviewNodeResponse> refreshPreview(Long id);

    /** 重拉后按勾选增量导入：已存在的更新参数，新的入库 */
    void importNodes(Long id, NodeGroupImportRequest request);

    /** 删除分组并连带删除组内节点；组内有节点被用户绑定则整体拒绝 */
    void delete(Long id);
}
