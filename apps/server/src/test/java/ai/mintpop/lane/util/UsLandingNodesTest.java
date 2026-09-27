package ai.mintpop.lane.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("美国落地判定：只认美国国旗或 [US]，宁可漏判不可误判")
class UsLandingNodesTest {

    @ParameterizedTest
    @DisplayName("命中的写法")
    @ValueSource(strings = {
            "🇺🇸[US]Santa Clara 01-GPT优化",
            "[US]San Jose07",
            "【US】San Jose08",
            "🇺🇸LosAngeles",
    })
    void matchesUsNodeNames(String name) {
        assertThat(UsLandingNodes.isUsLanding(name)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("不该命中的写法——收窄正是为了挡住这些")
    @ValueSource(strings = {
            "🇭🇰[HK]HongKong01-GPT优化",
            "🇷🇺[RU]Russia-Moscow",      // 裸 US 会命中 Russia
            "🇧🇾[BY]Belarus",             // 同上
            "🇨🇾[CY]Cyprus",              // 同上
            "Bonus 节点",                  // 同上
            "完美线路",                     // 裸「美」会命中
            "南美-圣保罗",                  // 同上
            "United States 03",             // 只认国旗与方括号国别码，全称不认
            "美国-洛杉矶 01",                // 同上，中文国名也不认
            "[境外用户专用]GPT01",           // 现役机场里名字不带国别的节点
    })
    void doesNotMatchNonUsNames(String name) {
        assertThat(UsLandingNodes.isUsLanding(name)).isFalse();
    }

    @Test
    @DisplayName("名字为 null 时判为不是美国节点，不抛")
    void treatsNullAsNotUs() {
        assertThat(UsLandingNodes.isUsLanding(null)).isFalse();
    }
}
