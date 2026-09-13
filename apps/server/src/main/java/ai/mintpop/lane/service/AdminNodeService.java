package ai.mintpop.lane.service;

import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.request.NodeSaveRequest;
import ai.mintpop.lane.response.AdminNodeResponse;
import ai.mintpop.lane.response.NodeProbeResponse;

import java.util.List;

public interface AdminNodeService {

    /** 列出节点；role 为 null 时返回全部 */
    List<AdminNodeResponse> list(NodeRole role);

    /** 新建节点，返回新节点 id */
    Long create(NodeSaveRequest request);

    void update(Long id, NodeSaveRequest request);

    void delete(Long id);

    /** 经落地节点探测实际出口 IP 并与登记值比对；只对 LAND 节点开放 */
    NodeProbeResponse probe(Long id);
}
