package ai.mintpop.lane.client;

import java.util.Optional;

/**
 * IP → ASN（自治系统号）/ 运营商查询口。采购尽调用它判断中转入口是否又落在同一家云厂商
 * （如又一个 AWS 东京）；三期的链路上报用它把上报请求的来源 IP 反查成运营商，
 * 落 {@code link_report.isp}——运营商由服务端反查而不让客户端自报（spec §8.1：自报不可信）。
 */
public interface IpAsnClient {

    /**
     * 反查结果。
     *
     * @param asn 形如 {@code "AS4134"} 的 ASN，非空
     * @param isp 可读的运营商名（如 {@code "China Telecom"}）；上游没给或给了空白时为 {@code null}，
     *            由调用方决定退回什么（链路上报退回 ASN 串）
     */
    record AsnInfo(String asn, String isp) {
    }

    /** @return 来源 IP 的 ASN 与运营商；查不到或查询失败返回空，不抛 */
    Optional<AsnInfo> lookup(String ip);

    /**
     * @return 形如 "AS16509" 的 ASN；查不到或查询失败返回空，不抛。
     * 只关心 ASN 的老调用方（尽调、入口 IP 巡检）继续用这个方法，不必改成解构 {@link AsnInfo}
     */
    default Optional<String> lookupAsn(String ip) {
        return lookup(ip).map(AsnInfo::asn);
    }
}
