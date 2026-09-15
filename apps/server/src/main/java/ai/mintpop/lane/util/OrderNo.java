package ai.mintpop.lane.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 订单号：LN + UTC 时间戳（yyyyMMddHHmmss）+ 6 位随机数字，共 22 位。
 * 前缀标业务线（多业务共用 Stripe 账户时人眼可辨来源），时间戳给排障，随机段防同秒撞号；
 * 唯一性仍由 plan_order 的唯一键兜底，调用方撞键重试。
 */
public final class OrderNo {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderNo() {
    }

    public static String generate(Instant now) {
        return "LN" + TS.format(now) + String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
