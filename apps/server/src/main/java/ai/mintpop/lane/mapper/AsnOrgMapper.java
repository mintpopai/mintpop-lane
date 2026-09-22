package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.AsnOrg;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** asn_org 表的 SQL 层。查询由 BaseMapper 提供，「有则不动」的写入需要 INSERT IGNORE，单写一条。 */
@Mapper
public interface AsnOrgMapper extends BaseMapper<AsnOrg> {

    /**
     * 首次见到该 ASN 时记下展示名；已存在则整行不动（包括 first_seen_at）。
     * <p>
     * 刻意用 {@code INSERT IGNORE} 而不是 {@code ON DUPLICATE KEY UPDATE}：展示名要钉在
     * 「第一次见到时的样子」，上游改口径不该把历史里的名字一起改掉，否则同一个 ASN 的展示名
     * 会随最近一次反查结果漂移。并发下两个请求同时插同一个 ASN 时，后到的那条被静默忽略，
     * 正是想要的语义——不抛主键冲突、不需要先查后插。
     */
    @Insert("INSERT IGNORE INTO asn_org (asn, org_name, first_seen_at) "
            + "VALUES (#{asn}, #{orgName}, #{firstSeenAt})")
    void insertIgnore(AsnOrg row);
}
