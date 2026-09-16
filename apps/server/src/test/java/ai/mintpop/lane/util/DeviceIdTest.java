package ai.mintpop.lane.util;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceIdTest {

    private static final String VALID = "a".repeat(64);

    @Test
    @DisplayName("合法机器码原样返回")
    void acceptsLowercaseHexOf64() {
        assertThat(DeviceId.normalize(VALID)).isEqualTo(VALID);
    }

    @Test
    @DisplayName("大写十六进制统一成小写：同一台机器不该因为客户端大小写不同被当成两台")
    void normalizesToLowercase() {
        assertThat(DeviceId.normalize("A".repeat(64))).isEqualTo(VALID);
    }

    @Test
    @DisplayName("缺失、空白、长度不对、含非十六进制字符，一律按缺少本机标识拒绝")
    void rejectsAnythingElse() {
        for (String bad : new String[] {null, "", "   ", "a".repeat(63), "a".repeat(65), "g".repeat(64)}) {
            assertThatThrownBy(() -> DeviceId.normalize(bad))
                    .isInstanceOf(BizException.class)
                    .hasFieldOrPropertyWithValue("bizCode.code", BizCodeEnum.DEVICE_ID_MISSING.getCode());
        }
    }

    @Test
    @DisplayName("tryNormalize 合法机器码归一化后装进 Optional")
    void tryNormalizeAcceptsValid() {
        assertThat(DeviceId.tryNormalize("A".repeat(64))).contains(VALID);
    }

    @Test
    @DisplayName("tryNormalize 对缺失与形状不对的一律返回空，不抛异常：调用方是顺带记录活跃时刻的旁路，不该因此中断请求")
    void tryNormalizeReturnsEmptyInsteadOfThrowing() {
        for (String bad : new String[] {null, "", "   ", "a".repeat(63), "a".repeat(65), "g".repeat(64)}) {
            assertThat(DeviceId.tryNormalize(bad)).isEmpty();
        }
    }
}
