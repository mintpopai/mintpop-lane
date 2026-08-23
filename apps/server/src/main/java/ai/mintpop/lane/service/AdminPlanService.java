package ai.mintpop.lane.service;

import ai.mintpop.lane.request.PlanSaveRequest;
import ai.mintpop.lane.response.PlanResponse;

import java.util.List;

/** 套餐维护：管理端的增删改查 */
public interface AdminPlanService {

    List<PlanResponse> list();

    Long create(PlanSaveRequest request);

    void update(Long id, PlanSaveRequest request);

    void delete(Long id);
}
