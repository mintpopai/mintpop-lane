package ai.mintpop.lane.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentNoTest {

    @Test
    @DisplayName("分配号是 10 位大写 Crockford Base32：不含 I / L / O / U，也不含小写与连字符")
    void generatesTenCharCrockfordCode() {
        IntStream.range(0, 200).forEach(i -> {
            String no = AssignmentNo.generate();
            assertThat(no).hasSize(AssignmentNo.LENGTH);
            assertThat(no).matches("[0-9A-HJKMNP-TV-Z]{10}");
        });
    }

    @Test
    @DisplayName("连续生成不重复：短码空间足够大，唯一键兜底只应在极端情况下用到")
    void generatesDistinctCodes() {
        Set<String> seen = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> seen.add(AssignmentNo.generate()));
        assertThat(seen).hasSize(1000);
    }
}
