package ai.mintpop.lane.util;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 机器码的形状校验，全仓唯一一处。
 *
 * <p>机器码是客户端对硬件标识做的 SHA-256，小写十六进制定长 64。校验只管形状——
 * 服务端无从验证它是否真的来自那台机器，这一点见迁移脚本里写明的已知边界。
 *
 * <p>统一转小写是必要的：同一台机器不该因为客户端换了个大小写写法就被当成两台，
 * 那会让用户平白多出一次换机申请。
 */
public final class DeviceId {

    private static final Pattern SHAPE = Pattern.compile("^[0-9a-f]{64}$");

    private DeviceId() {
    }

    /** 校验并归一化。缺失或形状不对一律抛 DEVICE_ID_MISSING——对客户端来说这两种情况该做的事一样：升级 */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(BizCodeEnum.DEVICE_ID_MISSING);
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!SHAPE.matcher(normalized).matches()) {
            throw new BizException(BizCodeEnum.DEVICE_ID_MISSING);
        }
        return normalized;
    }
}
