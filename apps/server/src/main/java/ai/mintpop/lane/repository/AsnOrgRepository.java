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
     *
     * @param firstSeenAt 首次见到的时间（UTC），由调用方从注入的 Clock 取——仓储不自己取「现在」
     */
    void insertIfAbsent(String asn, String orgName, Instant firstSeenAt);

    /** 全表 asn -> org_name，几十行量级，调用方一次读全表后在内存里按 ASN 取展示名 */
    Map<String, String> findAllNames();
}
