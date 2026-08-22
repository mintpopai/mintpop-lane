package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeRole;

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
     * <p>
     * 用实体方式更新以便 JSON 列的 typeHandler 生效，代价是 **null 字段会被跳过**：
     * <ul>
     *   <li>{@code extraConfig} 传空集合可以把该字段覆盖为空；{@code egressIp} 列上有
     *       updateStrategy = ALWAYS，传 null 即清空；</li>
     *   <li>{@code secret} 传 null 或空 Map 都表示「沿用原有敏感键」，**无法通过 update
     *       清空**——这与管理端「敏感键留空即不改」的语义一致（页面上看不到原密码，
     *       没重填就不该被清掉）。</li>
     * </ul>
     * <p>
     * 调用前提：入参必须是先 {@link #findById(Long)} 拿到的完整 DTO，改动要改的字段后
     * 原样回传其余字段；如果直接 new 一个只填了部分字段的 DTO 调用本方法，
     * {@code status} 会被静默重置成默认值 {@code ENABLED}，{@code extraConfig}
     * 会被覆盖成空集合，{@code egressIp} 会被清空。
     */
    void update(ProxyNodeDto node);

    void deleteById(Long id);

    boolean existsByName(String name);

    /** 同一分组内按订阅原始节点名取节点，重新拉取时的匹配键 */
    Optional<ProxyNodeDto> findByGroupIdAndSourceName(Long groupId, String sourceName);

    /** 某分组下的全部节点，按 id 升序 */
    List<ProxyNodeDto> findByGroupId(Long groupId);

    /** 某分组下的节点数，分组列表展示用 */
    long countByGroupId(Long groupId);
}
