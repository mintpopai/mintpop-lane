package ai.mintpop.lane.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDtoTest {

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    @DisplayName("起止都在时按「起含止不含」判定在期")
    void activeWithinWindow() {
        SubscriptionDto s = new SubscriptionDto();
        s.setStartsAt(NOW.minusSeconds(1));
        s.setEndsAt(NOW.plusSeconds(1));
        assertThat(s.isActiveAt(NOW)).isTrue();
        s.setEndsAt(NOW);
        assertThat(s.isActiveAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("待开通（起止为空）永远不算在期，也不抛空指针")
    void pendingIsNeverActive() {
        SubscriptionDto s = new SubscriptionDto();
        assertThat(s.isActiveAt(NOW)).isFalse();
        s.setStartsAt(NOW.minusSeconds(1));
        assertThat(s.isActiveAt(NOW)).isFalse();
    }
}
