package ai.mintpop.lane.repository;

import java.time.Instant;
import java.util.Map;

/**
 * ASN 到运营商展示名映射（asn_org）的读写口。上层只依赖这个接口，看不到 MyBatis-Plus。
 * <p>
 * 只有「记下来」与「全表读出来」两个动作，没有更新：展示名钉在第一次见到时的样子，
 * 上游改口径不改历史（理由见 {@link ai.mintpop.lane.entity.AsnOrg}）。
 */
public interface AsnOrgRepository {

    /**
     * 首次见到该 ASN 时记下展示名；已存在则不覆盖（INSERT IGNORE）。
     * <p>
     * 实现必须自带两道守卫。{@code INSERT IGNORE} 会把本该报错的写入统统降级成警告，配上
     * 「有则不动」，一个写坏的值会永久留在表里再也盖不掉：
     * <ul>
     *   <li>{@code orgName} 为 {@code null} 或全空白：整条<b>不写</b>。{@code org_name} 是
     *       {@code NOT NULL}，交给数据库的话 null 会被悄悄写成空串，这个 ASN 的展示名就被
     *       空串永久钉死，此后真名字来了也写不进来；</li>
     *   <li>超过 {@link ai.mintpop.lane.client.IpAsnClient#ISP_MAX_LENGTH} 的名字：先用
     *       {@link ai.mintpop.lane.client.IpAsnClient#truncateIsp} 截断再写——落库的值要由我们
     *       决定（trim 首尾空白、按码点切），而不是由 MySQL 的静默截断决定；也不能指望
     *       {@code IGNORE} 一直在，改成普通 {@code INSERT} 时不截就是 {@code Data too long}。</li>
     * </ul>
     * 调用方自己也可以先判空、先截断（链路上报就是这么做的），两层守卫不矛盾：调用方管的是
     * 「要不要记」，这里管的是「这张表不许被写坏」。
     *
     * @param firstSeenAt 首次见到的时间（UTC），由调用方从注入的 Clock 取——仓储不自己取「现在」
     */
    void insertIfAbsent(String asn, String orgName, Instant firstSeenAt);

    /** 全表 asn -> org_name，几十行量级，调用方一次读全表后在内存里按 ASN 取展示名 */
    Map<String, String> findAllNames();
}
