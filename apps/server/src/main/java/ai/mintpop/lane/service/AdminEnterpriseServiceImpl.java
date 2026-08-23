package ai.mintpop.lane.service;

import ai.mintpop.lane.entity.Enterprise;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.EnterpriseRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.request.EnterpriseSaveRequest;
import ai.mintpop.lane.response.EnterpriseResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Service
public class AdminEnterpriseServiceImpl implements AdminEnterpriseService {

    private final EnterpriseRepository enterpriseRepository;
    private final SubscriptionRepository subscriptionRepository;

    public AdminEnterpriseServiceImpl(EnterpriseRepository enterpriseRepository,
                                      SubscriptionRepository subscriptionRepository) {
        this.enterpriseRepository = enterpriseRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public List<EnterpriseResponse> list() {
        return enterpriseRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public Long create(EnterpriseSaveRequest request) {
        String domain = normalizeDomain(request.getDomain());
        if (enterpriseRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.ENTERPRISE_NAME_DUPLICATED);
        }
        if (enterpriseRepository.existsByDomain(domain)) {
            throw new BizException(BizCodeEnum.ENTERPRISE_DOMAIN_DUPLICATED);
        }
        Enterprise enterprise = new Enterprise();
        apply(enterprise, request, domain);
        return wrapUniqueViolation(() -> enterpriseRepository.create(enterprise));
    }

    @Override
    public void update(Long id, EnterpriseSaveRequest request) {
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.ENTERPRISE_NOT_FOUND));
        String domain = normalizeDomain(request.getDomain());
        // 重复检查按 id 排除自身：表是 ai_ci 排序规则，只改大小写时 existsByXxx 会匹配到自己
        if (enterpriseRepository.existsByNameExcludingId(request.getName(), id)) {
            throw new BizException(BizCodeEnum.ENTERPRISE_NAME_DUPLICATED);
        }
        if (enterpriseRepository.existsByDomainExcludingId(domain, id)) {
            throw new BizException(BizCodeEnum.ENTERPRISE_DOMAIN_DUPLICATED);
        }
        apply(enterprise, request, domain);
        wrapUniqueViolation(() -> {
            enterpriseRepository.update(enterprise);
            return null;
        });
    }

    @Override
    public void delete(Long id) {
        enterpriseRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.ENTERPRISE_NOT_FOUND));
        // 企业不设外键，引用关系由这里把关：删掉被订阅引用的企业会让归属信息静默丢失
        if (subscriptionRepository.existsByEnterpriseId(id)) {
            throw new BizException(BizCodeEnum.ENTERPRISE_IN_USE);
        }
        enterpriseRepository.deleteById(id);
    }

    /** 域名一律小写入库：同一个域名不该因为大小写写法不同而建出两家企业 */
    private String normalizeDomain(String domain) {
        return domain == null ? null : domain.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 唯一约束的兜底：预检查给的是可读错误，但两个管理员同时提交仍可能撞车，
     * 那时靠数据库的唯一索引挡住。企业有两个唯一索引，按索引名分辨是撞了名称还是域名，
     * 否则域名撞车会错报成「企业名已存在」，管理员照着改名也修不好。
     */
    private <T> T wrapUniqueViolation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            throw new BizException(message.contains("uk_enterprise_domain")
                    ? BizCodeEnum.ENTERPRISE_DOMAIN_DUPLICATED
                    : BizCodeEnum.ENTERPRISE_NAME_DUPLICATED);
        }
    }

    private void apply(Enterprise enterprise, EnterpriseSaveRequest request, String domain) {
        enterprise.setName(request.getName());
        enterprise.setDomain(domain);
        enterprise.setAgentTypes(request.getAgentTypes());
        enterprise.setEnabled(request.getEnabled());
        enterprise.setRemark(request.getRemark());
    }

    private EnterpriseResponse toResponse(Enterprise enterprise) {
        return new EnterpriseResponse(enterprise.getId(), enterprise.getName(), enterprise.getDomain(),
                enterprise.getAgentTypes(), enterprise.getEnabled(), enterprise.getRemark(),
                enterprise.getCreatedAt(), enterprise.getUpdatedAt());
    }
}
