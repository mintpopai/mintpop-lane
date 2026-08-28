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
import ai.mintpop.lane.util.AssignmentNo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    /** 分配号撞唯一键后的重试次数上限，见 createWithUniqueAssignmentNo */
    private static final int ASSIGNMENT_NO_MAX_ATTEMPTS = 5;

    /** 允许的偏差：签发时给了一天缓冲，判定时同样容忍一天 */
    private static final Duration CREDENTIAL_SYNC_TOLERANCE = Duration.ofDays(1);

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
        String credential = blankToNull(request.getCredential());
        // Claude 席位的凭证只能走 OAuth 签发；手工录入会绕开签发流程留下来源不明、
        // 有效期未知的凭证。Codex 走完全不同的凭据体系，签发流程本就不支持它，不受此限。
        if (credential != null && plan.getAgentType() == AgentType.CLAUDE) {
            throw new BizException(BizCodeEnum.CREDENTIAL_MANUAL_NOT_ALLOWED);
        }
        s.setCredential(credential);
        s.setRemark(request.getRemark());
        return createWithUniqueAssignmentNo(s);
    }

    /**
     * 落库，并保证分配号唯一。分配号是 10 位短码，不像 UUID 那样能「生成即认定唯一」，
     * 撞上唯一键就换一个再试。试满仍撞说明撞的不是分配号（碰撞概率在本项目量级下可忽略），
     * 而是别处出了问题，异常照原样抛出去，不要静默吞掉。
     */
    private Long createWithUniqueAssignmentNo(SubscriptionDto s) {
        for (int attempt = 1; ; attempt++) {
            s.setAssignmentNo(AssignmentNo.generate());
            try {
                return subscriptionRepository.create(s);
            } catch (DuplicateKeyException e) {
                if (attempt >= ASSIGNMENT_NO_MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    @Override
    @Transactional
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
            // Claude 席位的凭证只能走 OAuth 签发，手工录入一律拒绝；Codex 不受此限，见 create() 同款注释
            if (s.getAgentType() == AgentType.CLAUDE) {
                throw new BizException(BizCodeEnum.CREDENTIAL_MANUAL_NOT_ALLOWED);
            }
            s.setCredential(credential);
        }
        s.setRemark(request.getRemark());
        subscriptionRepository.update(s);
        if (credential != null) {
            // 手工录入的凭证来源不明、有效期未知，不得继承上一次签发的元数据，
            // 否则后台会显示「还有半年到期」而实际凭证可能早已失效。
            // 清空 scope 后客户端自动退回旧式行为（不注入 CLAUDE_CODE_OAUTH_SCOPES），语义正确。
            // 走专用的 clearCredentialMetadata，不占用上面常规 update() 的 SQL 列
            // （那五列本就不在常规更新路径里，避免把它们纳入日常更新引入静默覆盖风险）。
            subscriptionRepository.clearCredentialMetadata(id);
        }
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AdminSubscriptionResponse toResponse(SubscriptionDto s) {
        return new AdminSubscriptionResponse(
                s.getId(), s.getAssignmentNo(), s.getUserId(), s.getEnterpriseId(), s.getAgentType(),
                s.getPlanId(), s.getName(), s.getPlanDurationDays(), s.getPlanPrice(), s.getPlanCurrency(),
                s.getStartsAt(), s.getEndsAt(), s.getAccountEmail(),
                s.getCredential() != null && !s.getCredential().isBlank(),
                s.getCredentialExpiresAt(),
                isCredentialStale(s.getCredentialExpiresAt(), s.getEndsAt()),
                s.getRemark(), s.getCreatedAt(), s.getUpdatedAt());
    }

    /**
     * 凭证到期日是否已与订阅止期脱节。
     *
     * 当前系统没有独立的「续订」：套餐与时长分配后不可改，止期随起期重算，
     * 换套餐则删除后重新分配。所以事实上的续期就是改起期，
     * 触发点因此是「止期变化」而非某个续订事件。
     *
     * 两个方向都要管：凭证早于订阅到期会让用户续了期却突然断掉；
     * 凭证晚于订阅到期则是超发，正是本次要堵的漏洞。
     */
    static boolean isCredentialStale(Instant credentialExpiresAt, Instant endsAt) {
        if (credentialExpiresAt == null || endsAt == null) {
            return false;
        }
        Instant expected = endsAt.plus(CREDENTIAL_SYNC_TOLERANCE);
        return Duration.between(expected, credentialExpiresAt).abs().compareTo(CREDENTIAL_SYNC_TOLERANCE) > 0;
    }
}
