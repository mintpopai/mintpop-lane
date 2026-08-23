package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.SubscriptionCreateRequest;
import ai.mintpop.lane.request.SubscriptionUpdateRequest;
import ai.mintpop.lane.response.AdminSubscriptionResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;

    public AdminSubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                        UserRepository userRepository,
                                        PlanRepository planRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
    }

    @Override
    public List<AdminSubscriptionResponse> listByUser(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        return subscriptionRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Override
    public Long create(Long userId, SubscriptionCreateRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        Plan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new BizException(BizCodeEnum.PLAN_NOT_FOUND));
        if (!Boolean.TRUE.equals(plan.getEnabled())) {
            throw new BizException(BizCodeEnum.PLAN_DISABLED);
        }

        Instant startsAt = request.getStartsAt() != null ? request.getStartsAt() : Instant.now();
        SubscriptionDto s = new SubscriptionDto();
        s.setAssignmentNo(newAssignmentNo());
        s.setUserId(userId);
        // 套餐信息落快照（含 agent 类型）：套餐之后改名改价改类型甚至被删，都不影响这一次分配的记录
        s.setAgentType(plan.getAgentType());
        s.setPlanId(plan.getId());
        s.setName(plan.getName());
        s.setPlanDurationDays(plan.getDurationDays());
        s.setPlanPrice(plan.getPrice());
        s.setPlanCurrency(plan.getCurrency());
        s.setStartsAt(startsAt);
        s.setEndsAt(startsAt.plus(plan.getDurationDays(), ChronoUnit.DAYS));
        s.setCredential(blankToNull(request.getCredential()));
        s.setRemark(request.getRemark());
        return subscriptionRepository.create(s);
    }

    @Override
    public void update(Long id, SubscriptionUpdateRequest request) {
        SubscriptionDto s = subscriptionRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));

        s.setStartsAt(request.getStartsAt());
        // 止期不可直接改：始终按分配时的套餐时长快照随起期重算
        s.setEndsAt(request.getStartsAt().plus(s.getPlanDurationDays(), ChronoUnit.DAYS));
        // 凭据留空表示沿用原值：页面上看不到原凭据，不能因为没重填就把它清掉
        String credential = blankToNull(request.getCredential());
        if (credential != null) {
            s.setCredential(credential);
        }
        s.setRemark(request.getRemark());
        subscriptionRepository.update(s);
    }

    @Override
    public void delete(Long id) {
        subscriptionRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));
        subscriptionRepository.deleteById(id);
    }

    /** 分配号：不可猜测的 32 位十六进制 UUID，对外引用一律用它、不用自增 id */
    private static String newAssignmentNo() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AdminSubscriptionResponse toResponse(SubscriptionDto s) {
        return new AdminSubscriptionResponse(
                s.getId(), s.getAssignmentNo(), s.getUserId(), s.getAgentType(),
                s.getPlanId(), s.getName(), s.getPlanDurationDays(), s.getPlanPrice(), s.getPlanCurrency(),
                s.getStartsAt(), s.getEndsAt(),
                s.getCredential() != null && !s.getCredential().isBlank(),
                s.getRemark(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
