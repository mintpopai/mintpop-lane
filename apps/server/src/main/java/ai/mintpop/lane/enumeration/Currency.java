package ai.mintpop.lane.enumeration;

import java.math.BigDecimal;

/** 套餐定价币种。前端 types.ts 有逐字镜像，改这里两端同步 */
public enum Currency {
    USD(2),
    CNY(2);

    /** 小数位数：最小货币单位 = 10^scale 分之一（USD 美分、CNY 分）。新币种必须显式给值 */
    private final int minorUnitScale;

    Currency(int minorUnitScale) {
        this.minorUnitScale = minorUnitScale;
    }

    public int minorUnitScale() {
        return minorUnitScale;
    }

    /**
     * 价格 → 最小货币单位整数（Stripe 的 amount 口径）。价格小数位超过币种小数位说明数据有问题，
     * 抛 ArithmeticException 而不是四舍五入——收款金额不能靠猜。
     */
    public long toMinorUnit(BigDecimal price) {
        return price.movePointRight(minorUnitScale).longValueExact();
    }
}
