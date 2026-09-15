package ai.mintpop.lane.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNoTest {

    @Test
    @DisplayName("订单号 = LN + UTC 时间戳 14 位 + 6 位随机数字，共 22 位")
    void shape() {
        String no = OrderNo.generate(Instant.parse("2026-09-15T08:30:05Z"));
        assertThat(no).matches("LN20260915083005[0-9]{6}");
    }

    @Test
    @DisplayName("同一秒内连续生成也不相同")
    void distinct() {
        Instant now = Instant.parse("2026-09-15T08:30:05Z");
        assertThat(OrderNo.generate(now)).isNotEqualTo(OrderNo.generate(now));
    }
}
