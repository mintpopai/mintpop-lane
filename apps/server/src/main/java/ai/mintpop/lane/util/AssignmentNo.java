package ai.mintpop.lane.util;

import java.security.SecureRandom;

/**
 * 分配号生成：10 位 Crockford Base32 大写短码。
 *
 * <p>分配号是「给用户看的号」——要能一眼读完、能在工单里口述、能手抄，所以字母表去掉了
 * I / L / O / U 这四个与 1 / 0 易混的字母，长度压到 10 位。入库一律不带连字符，
 * 分组形态（7K3M9-QX2FT）只在界面上拼，不进数据库。
 *
 * <p>随机源用 {@link SecureRandom}：分配号会出现在用户界面与工单里，不该能顺着猜出别人的号。
 * 10 位 ≈ 50 bit、空间 32^10 ≈ 1.1e15，本项目量级下碰撞可忽略；但短码终究不是 UUID，
 * 不能「生成即认定唯一」——唯一性由 subscription 表的唯一键兜底，由调用方负责冲突重试。
 */
public final class AssignmentNo {

    /** Crockford Base32 字母表：去掉 I / L / O / U */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 分配号长度（字符数），不含展示用的连字符 */
    public static final int LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AssignmentNo() {
    }

    /** 生成一个分配号。唯一性不由本方法保证，见类注释 */
    public static String generate() {
        char[] chars = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }
}
