package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.NodeGroupDto;

import java.util.List;
import java.util.Optional;

/** 节点分组的读写口。上层只依赖这个接口，看不到 MyBatis-Plus 与密文。 */
public interface NodeGroupRepository {

    Optional<NodeGroupDto> findById(Long id);

    /** 全部分组，按 id 升序 */
    List<NodeGroupDto> findAll();

    /** 新建，返回自增主键 */
    Long create(NodeGroupDto group);

    /** 按 id 更新。入参须是先 findById 拿到的完整 DTO */
    void update(NodeGroupDto group);

    void deleteById(Long id);

    boolean existsByName(String name);
}
