package com.mintpop.server.service;

import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.request.NodeSaveRequest;
import com.mintpop.server.response.AdminNodeResponse;

import java.util.List;

public interface AdminNodeService {

    /** 列出节点；role 为 null 时返回全部 */
    List<AdminNodeResponse> list(NodeRole role);

    /** 新建节点，返回新节点 id */
    Long create(NodeSaveRequest request);

    void update(Long id, NodeSaveRequest request);

    void delete(Long id);
}
