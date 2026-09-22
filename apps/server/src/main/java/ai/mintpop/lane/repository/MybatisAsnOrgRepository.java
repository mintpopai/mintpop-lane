package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.AsnOrg;
import ai.mintpop.lane.mapper.AsnOrgMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** ASN 到运营商展示名映射的 MySQL 实现。 */
@Repository
public class MybatisAsnOrgRepository implements AsnOrgRepository {

    private final AsnOrgMapper mapper;

    public MybatisAsnOrgRepository(AsnOrgMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertIfAbsent(String asn, String orgName, Instant firstSeenAt) {
        AsnOrg row = new AsnOrg();
        row.setAsn(asn);
        row.setOrgName(orgName);
        row.setFirstSeenAt(firstSeenAt);
        mapper.insertIgnore(row);
    }

    @Override
    public Map<String, String> findAllNames() {
        // 全表几十行，直接读出来在内存里成 Map；LinkedHashMap 保住查询顺序，
        // 让依赖它的断言与日志输出稳定可复现
        Map<String, String> names = new LinkedHashMap<>();
        for (AsnOrg row : mapper.selectList(null)) {
            names.put(row.getAsn(), row.getOrgName());
        }
        return names;
    }
}
