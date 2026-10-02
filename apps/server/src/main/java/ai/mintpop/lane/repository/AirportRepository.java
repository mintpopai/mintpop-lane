package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.Airport;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 机场的读写口。机场没有密文字段，直接以实体承载业务数据。 */
public interface AirportRepository {

    Optional<Airport> findById(Long id);

    /** 全部机场，按 id 升序 */
    List<Airport> findAll();

    /** 主用机场的 id：只有它们的订阅能被分配为用户的主用，其余只当备用 */
    default Set<Long> findPrimaryEnabledIds() {
        return findAll().stream()
                .filter(airport -> Boolean.TRUE.equals(airport.getPrimaryEnabled()))
                .map(Airport::getId)
                .collect(Collectors.toSet());
    }

    /** 新建，返回自增主键 */
    Long create(Airport airport);

    /** 按 id 更新。入参须是先 findById 拿到的完整实体 */
    void update(Airport airport);

    void deleteById(Long id);

    boolean existsByName(String name);

    /** 更新时的重名检查：表是 ai_ci 排序规则，只改大小写会匹配到自己，必须按 id 排除 */
    boolean existsByNameExcludingId(String name, Long excludeId);
}
