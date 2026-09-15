package ai.mintpop.lane.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyTest {

    @Test
    @DisplayName("USD 与 CNY 都是两位小数：价格乘 100 得最小单位整数")
    void minorUnitOfTwoDecimalCurrencies() {
        assertThat(Currency.USD.toMinorUnit(new BigDecimal("99.99"))).isEqualTo(9999L);
        assertThat(Currency.CNY.toMinorUnit(new BigDecimal("0.50"))).isEqualTo(50L);
        assertThat(Currency.USD.toMinorUnit(new BigDecimal("10"))).isEqualTo(1000L);
    }

    @Test
    @DisplayName("超出币种小数位的价格是脏数据，直接拒绝而不是四舍五入")
    void rejectsExtraPrecision() {
        assertThatThrownBy(() -> Currency.USD.toMinorUnit(new BigDecimal("1.005")))
                .isInstanceOf(ArithmeticException.class);
    }
}
