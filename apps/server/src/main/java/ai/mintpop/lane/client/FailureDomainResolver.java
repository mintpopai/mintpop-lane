package ai.mintpop.lane.client;

/**
 * 解析一个节点域名的「故障域」＝ 它的 CNAME 链终点。
 * 同一故障域下的节点共用一台中转入口机，彼此不构成冗余——
 * 冗余按这个值分散才有意义，按 IP 分散无效（中转入口的 TTL 常低至 30 秒、随时换）。
 */
public interface FailureDomainResolver {

    /**
     * @return CNAME 链的终点；无 CNAME 时返回 host 本身（自成一域）；
     *         解析失败或 CNAME 成环返回 null——调用方按「未知」处理，不写库、下轮再试
     */
    String resolve(String host);
}
