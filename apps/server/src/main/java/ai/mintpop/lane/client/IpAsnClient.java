package ai.mintpop.lane.client;

import java.util.Optional;

/**
 * IP → ASN（自治系统号）/ 运营商查询口。采购尽调用它判断中转入口是否又落在同一家云厂商
 * （如又一个 AWS 东京）；三期的链路上报用它把上报请求的来源 IP 反查成运营商，
 * 落 {@code link_report.source_asn}（运营商维度的键）与 {@code asn_org.org_name}（只做展示的名字）——
 * 运营商由服务端反查而不让客户端自报（spec §8.1：自报不可信）。
 */
public interface IpAsnClient {

    /**
     * 反查结果。
     *
     * @param asn 形如 {@code "AS4134"} 的 ASN，非空——运营商维度的键只认它
     * @param isp 可读的运营商名（如 {@code "China Telecom"}），只做展示；上游没给或给了空白时为
     *            {@code null}，此时调用方不记名字（链路上报据此跳过 {@code asn_org} 写入），
     *            展示的时候退回 ASN 串
     */
    record AsnInfo(String asn, String isp) {
    }

    /** @return 来源 IP 的 ASN 与运营商；查不到或查询失败返回空，不抛 */
    Optional<AsnInfo> lookup(String ip);

    /**
     * 运营商名允许的最大字符数：与 {@code asn_org.org_name} 的 {@code VARCHAR(64)} 列宽逐字对应
     * （{@code V24__asn_org.sql}）。运营商维度的键是 ASN，名字只进这一张映射表。
     * ipwho.is 的 {@code connection.isp} 是自由文本的组织名，实测能超过 64 字符
     * （如中国电信完整的英文注册名）。这张表用 {@code INSERT IGNORE} 写入，超长不会报错、
     * 而是被 MySQL <b>静默截断</b>（配上「有则不动」，截错了就再也改不掉），所以长度要由
     * 我们自己收口；哪天改成普通 {@code INSERT}，严格模式下则是直接抛 {@code Data too long}。
     * 改这张表的列宽时必须同步改这里，两处失配这条防线就形同虚设。
     */
    int ISP_MAX_LENGTH = 64;

    /**
     * 把运营商名截到 {@link #ISP_MAX_LENGTH} 字符：按 Unicode 码点而非 {@code String.length()}
     * 的 UTF-16 code unit 计数——{@code VARCHAR(64)} 是按字符计宽度，增补平面字符（代理对）
     * 用 {@code length()} 数会多算一倍，稳妥起见用 {@code codePoints()} 重组。
     * trim 放在截断之前，避免截断点卡在首尾空白上白占一个字符名额。
     * 会把运营商名往 {@code asn_org.org_name} 送的地方共用这一份实现（反查客户端
     * {@link ai.mintpop.lane.client.RestClientIpAsnClient} 在装配 {@link AsnInfo} 时截一次，
     * {@code LinkReportServiceImpl} 记展示名前截一次，{@code AsnOrgRepository} 的实现在落库前
     * 再兜一次），不许各写一份截断逻辑各写各的。
     *
     * @return {@code isp} 为 {@code null} 时原样返回 {@code null}；否则返回 trim 且截断后的运营商名
     */
    static String truncateIsp(String isp) {
        if (isp == null) {
            return null;
        }
        String trimmed = isp.trim();
        if (trimmed.codePointCount(0, trimmed.length()) <= ISP_MAX_LENGTH) {
            return trimmed;
        }
        int[] codePoints = trimmed.codePoints().limit(ISP_MAX_LENGTH).toArray();
        return new String(codePoints, 0, codePoints.length);
    }

    /**
     * @return 形如 "AS16509" 的 ASN；查不到或查询失败返回空，不抛。
     * 只关心 ASN 的老调用方（尽调、入口 IP 巡检）继续用这个方法，不必改成解构 {@link AsnInfo}
     */
    default Optional<String> lookupAsn(String ip) {
        return lookup(ip).map(AsnInfo::asn);
    }
}
