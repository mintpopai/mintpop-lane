package ai.mintpop.lane.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialLifetimeCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

    private final CredentialLifetimeCalculator calculator =
            new CredentialLifetimeCalculator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("一个月的订阅拿到约一个月的凭证，外加一天缓冲")
    void followsShortSubscription() {
        long seconds = calculator.secondsFor(NOW.plus(Duration.ofDays(30)));
        assertThat(seconds).isEqualTo(Duration.ofDays(31).toSeconds());
    }

    @Test
    @DisplayName("超过一年的订阅被压到 365 天：那是服务端的上限")
    void cappedAtOneYear() {
        long seconds = calculator.secondsFor(NOW.plus(Duration.ofDays(1000)));
        assertThat(seconds).isEqualTo(31536000L);
    }

    @Test
    @DisplayName("已过期或即将过期的订阅仍给出一天下限，避免请求被服务端拒绝")
    void flooredAtOneDay() {
        assertThat(calculator.secondsFor(NOW.minus(Duration.ofDays(5))))
                .isEqualTo(Duration.ofDays(1).toSeconds());
    }
}
