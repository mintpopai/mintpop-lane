package ai.mintpop.lane.service;

import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.response.UserPlanResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/** 用户侧套餐：只读上架中的套餐。管理端的增删改在 AdminPlanService。 */
@Service
public class PlanService {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public List<UserPlanResponse> listEnabled() {
        return planRepository.findEnabled().stream().map(UserPlanResponse::from).toList();
    }
}
