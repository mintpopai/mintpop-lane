package ai.mintpop.lane.service;

import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.request.PlanSaveRequest;
import ai.mintpop.lane.response.PlanResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Service
public class AdminPlanServiceImpl implements AdminPlanService {

    private final PlanRepository planRepository;

    public AdminPlanServiceImpl(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public List<PlanResponse> list() {
        return planRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public Long create(PlanSaveRequest request) {
        if (planRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.PLAN_NAME_DUPLICATED);
        }
        Plan plan = new Plan();
        apply(plan, request);
        return wrapUniqueViolation(() -> planRepository.create(plan));
    }

    @Override
    public void update(Long id, PlanSaveRequest request) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.PLAN_NOT_FOUND));
        // 重名检查按 id 排除自身：表是 ai_ci 排序规则，只改大小写时 existsByName 会匹配到自己
        if (planRepository.existsByNameExcludingId(request.getName(), id)) {
            throw new BizException(BizCodeEnum.PLAN_NAME_DUPLICATED);
        }
        apply(plan, request);
        wrapUniqueViolation(() -> {
            planRepository.update(plan);
            return null;
        });
    }

    @Override
    public void delete(Long id) {
        planRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.PLAN_NOT_FOUND));
        planRepository.deleteById(id);
    }

    /**
     * 唯一约束的兜底：预检查给的是可读错误，但两个管理员同时提交仍可能撞车，
     * 那时靠数据库的唯一索引挡住。与 {@code AdminNodeServiceImpl.wrapUniqueViolation} 同一模式。
     */
    private <T> T wrapUniqueViolation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(BizCodeEnum.PLAN_NAME_DUPLICATED);
        }
    }

    private void apply(Plan plan, PlanSaveRequest request) {
        plan.setName(request.getName());
        plan.setDurationDays(request.getDurationDays());
        plan.setPrice(request.getPrice());
        plan.setCurrency(request.getCurrency());
        plan.setEnabled(request.getEnabled());
        plan.setRemark(request.getRemark());
    }

    private PlanResponse toResponse(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getDurationDays(), plan.getPrice(),
                plan.getCurrency(), plan.getEnabled(), plan.getRemark(), plan.getCreatedAt(), plan.getUpdatedAt());
    }
}
