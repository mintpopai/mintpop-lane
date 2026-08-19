package ai.mintpop.pier.response;

import ai.mintpop.pier.enumeration.BizCodeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("成功响应的业务码为 0")
    void 成功响应的业务码为零() {
        ApiResponse<String> resp = ApiResponse.success("载荷");

        assertThat(resp.getCode()).isZero();
        assertThat(resp.getData()).isEqualTo("载荷");
        assertThat(resp.getMsg()).isNull();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("按枚举构造的失败响应带上枚举的码与文案")
    void 失败响应带上枚举的码与文案() {
        ApiResponse<Void> resp = ApiResponse.fail(BizCodeEnum.TOKEN_INVALID);

        assertThat(resp.getCode()).isEqualTo(210001);
        assertThat(resp.getMsg()).isEqualTo(BizCodeEnum.TOKEN_INVALID.getMessage());
        assertThat(resp.getData()).isNull();
        assertThat(resp.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("所有业务错误码都是 6 位且按模块分段")
    void 业务错误码都是六位且按模块分段() {
        for (BizCodeEnum code : BizCodeEnum.values()) {
            assertThat(code.getCode())
                    .as("错误码 %s 必须是 6 位", code.name())
                    .isBetween(110000, 999999);
            assertThat(code.getMessage()).isNotBlank();
        }
    }

    @Test
    @DisplayName("业务错误码不重复")
    void 业务错误码不重复() {
        long distinct = Arrays.stream(BizCodeEnum.values())
                .map(BizCodeEnum::getCode)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(BizCodeEnum.values().length);
    }
}
