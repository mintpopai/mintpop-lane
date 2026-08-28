package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.repository.EnterpriseRepository;
import ai.mintpop.lane.repository.PlanRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除订阅前尽力吊销上游凭证，是提前退订这个真实场景的关键一环，
 * 但吊销失败绝不能阻塞删除——管理员删除的意图必须被执行。
 */
class AdminSubscriptionDeleteRevokeTest {

    private static final Long SUBSCRIPTION_ID = 100L;

    private AdminSubscriptionServiceImpl newService(SubscriptionRepository subscriptionRepository,
                                                     CredentialIssueService credentialIssueService) {
        return new AdminSubscriptionServiceImpl(subscriptionRepository, mock(UserRepository.class),
                mock(PlanRepository.class), mock(EnterpriseRepository.class), credentialIssueService);
    }

    private SubscriptionDto subscriptionWithCredential(String credential) {
        SubscriptionDto s = new SubscriptionDto();
        s.setId(SUBSCRIPTION_ID);
        s.setUserId(1L);
        s.setCredential(credential);
        return s;
    }

    @Test
    @DisplayName("删除有凭证的订阅：先尝试吊销上游凭证")
    void deleteAttemptsRevokeWhenCredentialPresent() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        CredentialIssueService credentialIssueService = mock(CredentialIssueService.class);
        when(subscriptionRepository.findById(SUBSCRIPTION_ID))
                .thenReturn(Optional.of(subscriptionWithCredential("sk-ant-oat01-x")));
        when(credentialIssueService.revokeCredential(SUBSCRIPTION_ID)).thenReturn(true);

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, credentialIssueService);
        service.delete(SUBSCRIPTION_ID);

        verify(credentialIssueService).revokeCredential(SUBSCRIPTION_ID);
        verify(subscriptionRepository).deleteById(SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("删除订阅时吊销抛异常：删除仍然成功——吊销失败不得阻塞删除")
    void deleteSucceedsEvenWhenRevokeThrows() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        CredentialIssueService credentialIssueService = mock(CredentialIssueService.class);
        when(subscriptionRepository.findById(SUBSCRIPTION_ID))
                .thenReturn(Optional.of(subscriptionWithCredential("sk-ant-oat01-x")));
        doThrow(new RuntimeException("上游超时")).when(credentialIssueService).revokeCredential(SUBSCRIPTION_ID);

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, credentialIssueService);

        service.delete(SUBSCRIPTION_ID);

        verify(subscriptionRepository).deleteById(SUBSCRIPTION_ID);
    }

    @Test
    @DisplayName("删除无凭证的订阅：不触发吊销调用，直接删除")
    void deleteSkipsRevokeWhenNoCredential() {
        SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
        CredentialIssueService credentialIssueService = mock(CredentialIssueService.class);
        when(subscriptionRepository.findById(SUBSCRIPTION_ID))
                .thenReturn(Optional.of(subscriptionWithCredential(null)));

        AdminSubscriptionServiceImpl service = newService(subscriptionRepository, credentialIssueService);
        service.delete(SUBSCRIPTION_ID);

        verify(credentialIssueService, never()).revokeCredential(any());
        verify(subscriptionRepository).deleteById(SUBSCRIPTION_ID);
    }
}
