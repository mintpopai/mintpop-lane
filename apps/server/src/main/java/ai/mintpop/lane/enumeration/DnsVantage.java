package ai.mintpop.lane.enumeration;

/** DNS 解析视角：中转入口按运营商分线路返回不同 IP，所以同一个域名要从多个视角各解一次。 */
public enum DnsVantage {
    CHINA_TELECOM,
    CHINA_UNICOM,
    CHINA_MOBILE,
    OVERSEAS
}
