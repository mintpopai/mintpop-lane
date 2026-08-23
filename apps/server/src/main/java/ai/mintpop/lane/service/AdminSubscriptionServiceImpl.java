package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.Enterprise;
import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.enumeration.AgentType;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.EnterpriseRepository;
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
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final EnterpriseRepository enterpriseRepository;

    public AdminSubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                        UserRepository userRepository,
                                        PlanRepository planRepository,
                                        EnterpriseRepository enterpriseRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.enterpriseRepository = enterpriseRepository;
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
        String accountEmail = normalizeEmail(request.getAccountEmail());
        checkEnterpriseAccepts(request.getEnterpriseId(), plan.getAgentType(), accountEmail);

        Instant startsAt = request.getStartsAt() != null ? request.getStartsAt() : Instant.now();
        SubscriptionDto s = new SubscriptionDto();
        s.setAssignmentNo(newAssignmentNo());
        s.setUserId(userId);
        s.setEnterpriseId(request.getEnterpriseId());
        // 套餐信息落快照（含 agent 类型）：套餐之后改名改价改类型甚至被删，都不影响这一次分配的记录
        s.setAgentType(plan.getAgentType());
        s.setPlanId(plan.getId());
        s.setName(plan.getName());
        s.setPlanDurationDays(plan.getDurationDays());
        s.setPlanPrice(plan.getPrice());
        s.setPlanCurrency(plan.getCurrency());
        s.setStartsAt(startsAt);
        s.setEndsAt(startsAt.plus(plan.getDurationDays(), ChronoUnit.DAYS));
        s.setAccountEmail(accountEmail);
        s.setCredential(blankToNull(request.getCredential()));
        s.setRemark(request.getRemark());
        return subscriptionRepository.create(s);
    }

    @Override
    public void update(Long id, SubscriptionUpdateRequest request) {
        SubscriptionDto s = subscriptionRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));

        // 归属可改可清；agent 类型是分配时的快照，改归属时拿它去比对新企业支持的类型
        String accountEmail = normalizeEmail(request.getAccountEmail());
        checkEnterpriseAccepts(request.getEnterpriseId(), s.getAgentType(), accountEmail);
        s.setEnterpriseId(request.getEnterpriseId());
        // 账号是明文、页面看得见原值，留空即清除（与下面凭据的「沿用原值」相反）
        s.setAccountEmail(accountEmail);
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

    /**
     * 归属企业的把关：企业得存在、在启用中、支持这条订阅的 agent 类型，
     * 且分配出去的账号得是该企业域名下的邮箱。
     * enterpriseId 为空即个人订阅，一概不查（账号邮箱爱是哪个域名都行）。
     */
    private void checkEnterpriseAccepts(Long enterpriseId, AgentType agentType, String accountEmail) {
        if (enterpriseId == null) {
            return;
        }
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BizException(BizCodeEnum.ENTERPRISE_NOT_FOUND));
        if (!Boolean.TRUE.equals(enterprise.getEnabled())) {
            throw new BizException(BizCodeEnum.ENTERPRISE_DISABLED);
        }
        if (enterprise.getAgentTypes() == null || !enterprise.getAgentTypes().contains(agentType)) {
            throw new BizException(BizCodeEnum.ENTERPRISE_AGENT_TYPE_MISMATCH);
        }
        // 账号选填：没录就不比对；录了就必须落在企业域名下
        if (accountEmail != null && !domainOf(accountEmail).equals(enterprise.getDomain())) {
            throw new BizException(BizCodeEnum.SUBSCRIPTION_ACCOUNT_DOMAIN_MISMATCH);
        }
    }

    /** 取邮箱的域名段。入参已过 @Email 校验，必有 @；真没有就返回空串，比对必然失败 */
    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }

    /** 账号邮箱统一小写入库：企业域名也是小写存的，两边同一形态才好比对 */
    private static String normalizeEmail(String email) {
        String trimmed = blankToNull(email);
        return trimmed == null ? null : trimmed.trim().toLowerCase(Locale.ROOT);
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
                s.getId(), s.getAssignmentNo(), s.getUserId(), s.getEnterpriseId(), s.getAgentType(),
                s.getPlanId(), s.getName(), s.getPlanDurationDays(), s.getPlanPrice(), s.getPlanCurrency(),
                s.getStartsAt(), s.getEndsAt(), s.getAccountEmail(),
                s.getCredential() != null && !s.getCredential().isBlank(),
                s.getRemark(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
