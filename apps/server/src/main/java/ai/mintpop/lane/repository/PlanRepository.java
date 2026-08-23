package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.Plan;

import java.util.List;
import java.util.Optional;

/**
 * 套餐的读写口。上层只依赖这个接口。
 * 套餐没有密文字段，不需要 DTO 形态，直接以实体承载业务数据。
 */
public interface PlanRepository {

    Optional<Plan> findById(Long id);

    /** 全部套餐，按 id 升序 */
    List<Plan> findAll();

    /** 新建，返回自增主键 */
    Long create(Plan plan);

    /** 按 id 更新。入参须是先 findById 拿到的完整实体 */
    void update(Plan plan);

    void deleteById(Long id);

    boolean existsByName(String name);

    /**
     * 更新时的重名检查：排除自身那一行。
     * 表的排序规则是忽略大小写的 ai_ci，只改大小写的改名会让 existsByName 匹配到自己，
     * 必须按 id 排除，不能在 Java 层用 equals 比较新旧名字来代替。
     */
    boolean existsByNameExcludingId(String name, Long excludeId);
}
