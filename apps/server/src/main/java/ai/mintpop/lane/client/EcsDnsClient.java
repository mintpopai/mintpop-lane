package ai.mintpop.lane.client;

import java.util.List;

/**
 * 带 EDNS Client Subnet 的 A 记录解析口：中转入口常按运营商分线路返回不同 IP，
 * 用不同的代表性子网各查一次才能看全各视角的入口 IP。
 */
public interface EcsDnsClient {

    /**
     * @param host 待解析的域名（故障域）
     * @param clientSubnet EDNS Client Subnet，形如 "202.96.128.0/24"
     * @return 该视角解析到的 A 记录 IP 列表；任何失败（网络不通、限流、返回格式不认识）一律返回空列表并记日志，不抛
     */
    List<String> resolveA(String host, String clientSubnet);
}
