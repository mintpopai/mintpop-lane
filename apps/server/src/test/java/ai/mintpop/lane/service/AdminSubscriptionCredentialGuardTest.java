package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Claude 席位凭证只能走 OAuth 签发、不支持手工录入的收紧规则。
 * Codex 走完全不同的凭据体系，不受此限，创建与更新两条路径都要各自守住。
 */
class AdminSubscriptionCredentialGuardTest {

    private static final Long USER_ID = 1L;
    private static final Long PLAN_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 100L;

    private AdminSubscriptionServiceImpl newService(SubscriptionRepository subscriptionRepository,
                                                     UserRepository userRepository,
                                                     PlanRepository planRepository,
                                                     EnterpriseRepository enterpriseRepository) {
        // 本测试类只覆盖手工凭据的收紧规则，与吊销无关，注入一个不会被用到的 mock 即可
        return new AdminSubscriptionServiceImpl(subscriptionRepository, userRepository, planRepository,
                enterpriseRepository, mock(CredentialIssueService.class));
    }

    private Plan planOf(AgentType agentType) {
        Plan plan = new Plan();
        plan.setId(PLAN_ID);
        plan.setName("测试套餐");
        plan.setAgentType(agentType);
        plan.setDurationDays(30);
        plan.setEnabled(true);
        return plan;
    }

    @Test
    @DisplayName("创建 Claude 订阅时带手工凭据：拒绝，不落库")
    void createRejectsManualCredentialForClaude() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        EnterpriseRepository enterpriseRepository = mock(EnterpriseRepository.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new UserDto()));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planOf(AgentType.CLAUDE)));

        SubscriptionCreateRequest request = new SubscriptionCreateRequest();
        request.setPlanId(PLAN_ID);
        request.setCredential("sk-ant-oat01-manual");

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, userRepository, planRepository, enterpriseRepository);

        assertThatThrownBy(() -> service.create(USER_ID, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_MANUAL_NOT_ALLOWED);

        verify(subscriptionRepository, never()).create(any());
    }

    @Test
    @DisplayName("创建 Codex 订阅时带手工凭据：放行，Codex 不走 OAuth 签发体系")
    void createAllowsManualCredentialForCodex() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        EnterpriseRepository enterpriseRepository = mock(EnterpriseRepository.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new UserDto()));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planOf(AgentType.CODEX)));
        when(subscriptionRepository.create(any())).thenReturn(SUBSCRIPTION_ID);

        SubscriptionCreateRequest request = new SubscriptionCreateRequest();
        request.setPlanId(PLAN_ID);
        request.setCredential("codex-manual-credential");

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, userRepository, planRepository, enterpriseRepository);

        Long id = service.create(USER_ID, request);

        assertThat(id).isEqualTo(SUBSCRIPTION_ID);
        verify(subscriptionRepository).create(argThatCredentialEquals("codex-manual-credential"));
    }

    @Test
    @DisplayName("创建 Claude 订阅时凭据留空：放行，留空是正常的不填凭据语义")
    void createAllowsBlankCredentialForClaude() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        EnterpriseRepository enterpriseRepository = mock(EnterpriseRepository.class);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new UserDto()));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(planOf(AgentType.CLAUDE)));
        when(subscriptionRepository.create(any())).thenReturn(SUBSCRIPTION_ID);

        SubscriptionCreateRequest request = new SubscriptionCreateRequest();
        request.setPlanId(PLAN_ID);
        request.setCredential("   ");

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, userRepository, planRepository, enterpriseRepository);

        Long id = service.create(USER_ID, request);

        assertThat(id).isEqualTo(SUBSCRIPTION_ID);
        verify(subscriptionRepository).create(argThatCredentialEquals(null));
    }

    @Test
    @DisplayName("更新 Claude 订阅时带手工凭据：拒绝，不落库、不清元数据")
    void updateRejectsManualCredentialForClaude() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        EnterpriseRepository enterpriseRepository = mock(EnterpriseRepository.class);

        SubscriptionDto existing = existingSubscription(AgentType.CLAUDE);
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(existing));

        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest();
        request.setStartsAt(existing.getStartsAt());
        request.setCredential("sk-ant-oat01-manual");

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, userRepository, planRepository, enterpriseRepository);

        assertThatThrownBy(() -> service.update(SUBSCRIPTION_ID, request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getBizCode())
                .isEqualTo(BizCodeEnum.CREDENTIAL_MANUAL_NOT_ALLOWED);

        verify(subscriptionRepository, never()).update(any());
        verify(subscriptionRepository, never()).clearCredentialMetadata(any());
    }

    @Test
    @DisplayName("更新 Codex 订阅时带手工凭据：放行，且沿用既有清元数据行为")
    void updateAllowsManualCredentialForCodex() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        EnterpriseRepository enterpriseRepository = mock(EnterpriseRepository.class);

        SubscriptionDto existing = existingSubscription(AgentType.CODEX);
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(existing));

        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest();
        request.setStartsAt(existing.getStartsAt());
        request.setCredential("codex-manual-credential");

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, userRepository, planRepository, enterpriseRepository);

        service.update(SUBSCRIPTION_ID, request);

        verify(subscriptionRepository).update(argThatCredentialEquals("codex-manual-credential"));
        verify(subscriptionRepository).clearCredentialMetadata(SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("更新 Claude 订阅时凭据留空：放行，沿用原凭据、不触发清元数据")
    void updateAllowsBlankCredentialForClaude() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        PlanRepository planRepository = mock(PlanRepository.class);
        EnterpriseRepository enterpriseRepository = mock(EnterpriseRepository.class);

        SubscriptionDto existing = existingSubscription(AgentType.CLAUDE);
        existing.setCredential("sk-ant-oat01-issued");
        when(subscriptionRepository.findById(SUBSCRIPTION_ID)).thenReturn(Optional.of(existing));

        SubscriptionUpdateRequest request = new SubscriptionUpdateRequest();
        request.setStartsAt(existing.getStartsAt());
        request.setCredential("");
        request.setRemark("只改备注");

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, userRepository, planRepository, enterpriseRepository);

        service.update(SUBSCRIPTION_ID, request);

        verify(subscriptionRepository).update(argThatCredentialEquals("sk-ant-oat01-issued"));
        verify(subscriptionRepository, never()).clearCredentialMetadata(any());
    }

    private SubscriptionDto existingSubscription(AgentType agentType) {
        SubscriptionDto s = new SubscriptionDto();
        s.setId(SUBSCRIPTION_ID);
        s.setUserId(USER_ID);
        s.setAgentType(agentType);
        s.setPlanDurationDays(30);
        s.setStartsAt(Instant.parse("2026-08-01T00:00:00Z"));
        s.setEndsAt(Instant.parse("2026-08-31T00:00:00Z"));
        return s;
    }

    private static SubscriptionDto argThatCredentialEquals(String expectedCredential) {
        return org.mockito.ArgumentMatchers.argThat(s -> {
            if (expectedCredential == null) {
                return s.getCredential() == null;
            }
            return expectedCredential.equals(s.getCredential());
        });
    }
}
