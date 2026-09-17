package ai.mintpop.lane.client;

import java.util.Optional;

/**
 * IP → ASN（自治系统号）查询口。采购尽调用它判断中转入口是否又落在同一家云厂商（如又一个 AWS 东京）。
 */
public interface IpAsnClient {

    /** @return 形如 "AS16509" 的 ASN；查不到或查询失败返回空，不抛 */
    Optional<String> lookupAsn(String ip);
}
