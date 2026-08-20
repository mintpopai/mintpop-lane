package ai.mintpop.lane.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionDtoTest {

    @Test
    @DisplayName("在期判定：起含止不含，按 UTC 绝对时刻比较")
    void 起含止不含() {
        SubscriptionDto s = new SubscriptionDto();
        s.setStartsAt(Instant.parse("2026-08-01T00:00:00Z"));
        s.setEndsAt(Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(s.isActiveAt(Instant.parse("2026-07-31T23:59:59Z"))).isFalse();
        assertThat(s.isActiveAt(Instant.parse("2026-08-01T00:00:00Z"))).isTrue();   // 起：含
        assertThat(s.isActiveAt(Instant.parse("2026-08-31T23:59:59Z"))).isTrue();
        assertThat(s.isActiveAt(Instant.parse("2026-09-01T00:00:00Z"))).isFalse();  // 止：不含
    }
}
