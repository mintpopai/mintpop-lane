package ai.mintpop.lane.service;

import ai.mintpop.lane.request.EnterpriseSaveRequest;
import ai.mintpop.lane.response.EnterpriseResponse;

import java.util.List;

/** 企业维护：管理端的增删改查 */
public interface AdminEnterpriseService {

    List<EnterpriseResponse> list();

    Long create(EnterpriseSaveRequest request);

    void update(Long id, EnterpriseSaveRequest request);

    void delete(Long id);
}
