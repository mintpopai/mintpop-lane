package ai.mintpop.lane.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RebindRequestNoTest {

    @Test
    @DisplayName("形如 DR + 14 位 UTC 时间戳 + 6 位随机数字")
    void hasFixedShape() {
        String no = RebindRequestNo.generate(Instant.parse("2026-09-15T02:03:04Z"));

        assertThat(no).matches("^DR20260915020304\\d{6}$");
    }

    @Test
    @DisplayName("同一秒内连续生成不应撞号（随机段兜底，唯一性仍由唯一键保证）")
    void randomSegmentVaries() {
        Instant now = Instant.parse("2026-09-15T02:03:04Z");

        assertThat(RebindRequestNo.generate(now)).isNotEqualTo(RebindRequestNo.generate(now));
    }
}
