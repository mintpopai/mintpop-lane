package ai.mintpop.lane.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSubscriptionCredentialSyncTest {

    private static final Instant ENDS_AT = Instant.parse("2026-09-27T00:00:00Z");

    @Test
    @DisplayName("凭证到期日与订阅止期同步时不标记待更新")
    void inSyncIsNotStale() {
        Instant credentialExpiresAt = ENDS_AT.plus(Duration.ofDays(1));
        assertThat(AdminSubscriptionServiceImpl.isCredentialStale(credentialExpiresAt, ENDS_AT)).isFalse();
    }

    @Test
    @DisplayName("起期后移导致凭证早于订阅到期：用户续了期却会在老到期日断掉")
    void credentialExpiringEarlyIsStale() {
        Instant credentialExpiresAt = ENDS_AT.minus(Duration.ofDays(20));
        assertThat(AdminSubscriptionServiceImpl.isCredentialStale(credentialExpiresAt, ENDS_AT)).isTrue();
    }

    @Test
    @DisplayName("凭证晚于订阅到期属于超发，同样要标记")
    void credentialOutlivingSubscriptionIsStale() {
        Instant credentialExpiresAt = ENDS_AT.plus(Duration.ofDays(120));
        assertThat(AdminSubscriptionServiceImpl.isCredentialStale(credentialExpiresAt, ENDS_AT)).isTrue();
    }

    @Test
    @DisplayName("没有凭证或旧式凭证不参与判定")
    void missingCredentialIsNotStale() {
        assertThat(AdminSubscriptionServiceImpl.isCredentialStale(null, ENDS_AT)).isFalse();
    }
}
