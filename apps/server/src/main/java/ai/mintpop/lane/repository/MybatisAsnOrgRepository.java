package ai.mintpop.lane.repository;

import ai.mintpop.lane.client.IpAsnClient;
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
        // 两道守卫都是冲着 INSERT IGNORE 来的：IGNORE 会把本该报错的写入降级成警告，
        // null 被悄悄写成空串、超长被悄悄按列宽砍掉——既然「有则不动」，这一行写下去就改不掉了，
        // 于是一个本该报错的值会以 MySQL 自己挑的形态永久留在表里（实测：去掉这两道守卫后，
        // insertIfAbsent(asn, null, t) 真的落下了一行 org_name=''）。
        //
        // 没名字就别占这个键位：空串展示名比没有更糟——矩阵上多一个看不出是什么的表头，
        // 而且真名字来了也再写不进来
        if (orgName == null || orgName.isBlank()) {
            return;
        }
        AsnOrg row = new AsnOrg();
        row.setAsn(asn);
        // 超长在 Java 侧截，不留给 MySQL：截出来的值才是确定的（trim 掉首尾空白再按码点切），
        // 也不指望 IGNORE 一直在——哪天改成普通 INSERT，不截就是严格模式下的 Data too long。
        // 截断逻辑共用 IpAsnClient 那一份，不在这里另写一套
        row.setOrgName(IpAsnClient.truncateIsp(orgName));
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
