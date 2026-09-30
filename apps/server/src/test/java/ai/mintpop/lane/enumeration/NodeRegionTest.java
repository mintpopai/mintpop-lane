package ai.mintpop.lane.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("地区枚举按节点名判定")
class NodeRegionTest {

    @Test
    @DisplayName("美国：国旗、[US]、【US】、「美国」命中其一即算，大小写不敏感")
    void usMatchesFlagOrBracketedCode() {
        assertThat(NodeRegion.US.matches("🇺🇸[US]Santa Clara 01-GPT优化")).isTrue();
        assertThat(NodeRegion.US.matches("[us] Los Angeles")).isTrue();
        assertThat(NodeRegion.US.matches("【US】西雅图")).isTrue();
        assertThat(NodeRegion.US.matches("美国洛杉矶 01")).isTrue();
    }

    @Test
    @DisplayName("裸 US、United States、单字「美」、美西一律不认；null 不认")
    void usRejectsLooseSpellings() {
        assertThat(NodeRegion.US.matches("Russia US East")).isFalse();
        assertThat(NodeRegion.US.matches("United States 01")).isFalse();
        assertThat(NodeRegion.US.matches("美西 01")).isFalse();
        assertThat(NodeRegion.US.matches("南美 智利")).isFalse();
        assertThat(NodeRegion.US.matches(null)).isFalse();
    }

    @Test
    @DisplayName("展示名给管理端下拉用")
    void displayName() {
        assertThat(NodeRegion.US.getDisplayName()).isEqualTo("美国");
    }
}
