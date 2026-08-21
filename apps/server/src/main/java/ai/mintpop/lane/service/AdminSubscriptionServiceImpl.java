package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.SubscriptionSaveRequest;
import ai.mintpop.lane.response.AdminSubscriptionResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminSubscriptionServiceImpl implements AdminSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public AdminSubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                        UserRepository userRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<AdminSubscriptionResponse> listByUser(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        return subscriptionRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Override
    public Long create(Long userId, SubscriptionSaveRequest request) {
        userRepository.findById(userId).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        validatePeriod(request);

        SubscriptionDto s = new SubscriptionDto();
        s.setUserId(userId);
        s.setAgentType(request.getAgentType());
        s.setName(request.getName());
        s.setStartsAt(request.getStartsAt());
        s.setEndsAt(request.getEndsAt());
        s.setCredential(blankToNull(request.getCredential()));
        s.setRemark(request.getRemark());
        return subscriptionRepository.create(s);
    }

    @Override
    public void update(Long id, SubscriptionSaveRequest request) {
        SubscriptionDto s = subscriptionRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.SUBSCRIPTION_NOT_FOUND));
        validatePeriod(request);

        s.setAgentType(request.getAgentType());
        s.setName(request.getName());
        s.setStartsAt(request.getStartsAt());
        s.setEndsAt(request.getEndsAt());
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

    /** 止期必须晚于起期；相等的「零时长订阅」没有意义，一并拒绝 */
    private static void validatePeriod(SubscriptionSaveRequest request) {
        if (!request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AdminSubscriptionResponse toResponse(SubscriptionDto s) {
        return new AdminSubscriptionResponse(
                s.getId(), s.getUserId(), s.getAgentType(), s.getName(),
                s.getStartsAt(), s.getEndsAt(),
                s.getCredential() != null && !s.getCredential().isBlank(),
                s.getRemark(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
