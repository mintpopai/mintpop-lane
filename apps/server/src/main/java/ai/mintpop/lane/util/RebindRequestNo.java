package ai.mintpop.lane.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 换机申请号：DR + UTC 时间戳（yyyyMMddHHmmss）+ 6 位随机数字，共 22 位。
 * 前缀标事件类型（飞书里一眼看出是换机申请），时间戳给排障，随机段防同秒撞号；
 * 唯一性仍由 device_rebind_request 的唯一键兜底，调用方撞键重试。
 */
public final class RebindRequestNo {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    private RebindRequestNo() {
    }

    public static String generate(Instant now) {
        return "DR" + TS.format(now) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
