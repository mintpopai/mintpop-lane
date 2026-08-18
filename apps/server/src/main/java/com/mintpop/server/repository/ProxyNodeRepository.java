package com.mintpop.server.repository;

import com.mintpop.server.dto.ProxyNodeDto;
import com.mintpop.server.enumeration.NodeRole;

import java.util.List;
import java.util.Optional;

/**
 * 节点池的读写口。上层只依赖这个接口，看不到 MyBatis-Plus 与密文。
 */
public interface ProxyNodeRepository {

    Optional<ProxyNodeDto> findById(Long id);

    /** 按角色列出节点；role 为 null 时返回全部。按 id 升序。 */
    List<ProxyNodeDto> findAll(NodeRole role);

    /** 新建，返回自增主键 */
    Long create(ProxyNodeDto node);

    /**
     * 按 id 更新。
     * 用实体方式更新以便 JSON 列的 typeHandler 生效，代价是 **null 字段会被跳过**——
     * 这正好对上管理端「敏感键留空即不改」的语义；需要清空某字段时传空集合而非 null。
     */
    void update(ProxyNodeDto node);

    void deleteById(Long id);

    boolean existsByName(String name);
}
