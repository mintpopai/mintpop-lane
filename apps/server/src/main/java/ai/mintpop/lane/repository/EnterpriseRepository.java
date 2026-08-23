package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.Enterprise;

import java.util.List;
import java.util.Optional;

/**
 * 企业的读写口。上层只依赖这个接口。
 * 企业没有密文字段，不需要 DTO 形态，直接以实体承载业务数据。
 */
public interface EnterpriseRepository {

    Optional<Enterprise> findById(Long id);

    /** 全部企业，按 id 升序 */
    List<Enterprise> findAll();

    /** 新建，返回自增主键 */
    Long create(Enterprise enterprise);

    /** 按 id 更新。入参须是先 findById 拿到的完整实体 */
    void update(Enterprise enterprise);

    void deleteById(Long id);

    boolean existsByName(String name);

    boolean existsByDomain(String domain);

    /**
     * 更新时的重名检查：排除自身那一行。
     * 表的排序规则是忽略大小写的 ai_ci，只改大小写的改名会让 existsByName 匹配到自己，
     * 必须按 id 排除，不能在 Java 层用 equals 比较新旧名字来代替。
     */
    boolean existsByNameExcludingId(String name, Long excludeId);

    /** 更新时的域名重复检查：排除自身那一行，理由同 existsByNameExcludingId */
    boolean existsByDomainExcludingId(String domain, Long excludeId);
}
