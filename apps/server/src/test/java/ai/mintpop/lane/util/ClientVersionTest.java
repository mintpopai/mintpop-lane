package ai.mintpop.lane.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientVersionTest {

    private static ClientVersion v(String raw) {
        return ClientVersion.parse(raw).orElseThrow();
    }

    @Test
    @DisplayName("解析 X.Y.Z，容忍 v 前缀与首尾空白")
    void parsesPlainAndPrefixed() {
        assertThat(v("1.2.3")).isEqualTo(new ClientVersion(1, 2, 3, null));
        assertThat(v(" v1.2.3 ")).isEqualTo(new ClientVersion(1, 2, 3, null));
        assertThat(v("1.2.3-rc.1")).isEqualTo(new ClientVersion(1, 2, 3, "rc.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "1.2", "1.2.3.4", "abc", "1.2.x", "99999999999.0.0"})
    @DisplayName("形状不对或数字溢出：解析为空")
    void rejectsMalformed(String raw) {
        assertThat(ClientVersion.parse(raw)).isEmpty();
    }

    @Test
    @DisplayName("null：解析为空")
    void rejectsNull() {
        assertThat(ClientVersion.parse(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "1.0.9, 1.1.0",
            // 按数值比，不按字典序
            "0.9.0, 0.10.0",
            "1.9.9, 2.0.0",
            // 预发布早于同号正式版
            "1.2.0-rc.1, 1.2.0",
            "1.2.0-beta.1, 1.2.0-rc.1",
    })
    @DisplayName("比较：左边严格早于右边")
    void ordersOlderFirst(String older, String newer) {
        assertThat(v(older).isOlderThan(v(newer))).isTrue();
        assertThat(v(newer).isOlderThan(v(older))).isFalse();
    }

    @Test
    @DisplayName("同版本不算更旧")
    void equalIsNotOlder() {
        assertThat(v("1.1.0").isOlderThan(v("v1.1.0"))).isFalse();
    }
}
