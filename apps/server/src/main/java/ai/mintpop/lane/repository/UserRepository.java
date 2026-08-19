package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.PageResult;
import ai.mintpop.lane.dto.UserDto;

import java.util.Optional;

/**
 * 用户的读写口。终端那条路（LinkService）与管理端那条路（AdminUserService）
 * 都只依赖这个接口，两边读同一张表的行为因此不会分叉。
 * 返回的一律是明文 DTO，密文与 MyBatis-Plus 都被挡在实现之下。
 */
public interface UserRepository {

    Optional<UserDto> findBySubject(String subject);

    Optional<UserDto> findById(Long id);

    /** 反查某个落地节点当前被谁占用 */
    Optional<UserDto> findByLandNodeId(Long landNodeId);

    /** 分页搜索；keyword 为空时不过滤，非空时匹配姓名或 subject。按 id 升序。 */
    PageResult<UserDto> search(String keyword, long pageNo, long pageSize);

    /** 新建，返回自增主键 */
    Long create(UserDto user);

    /**
     * 按 id 更新全部可变字段。
     * 与常见的「null 字段跳过」不同，这里的 null 会被显式写进库——
     * 取消落地分配（landNodeId = null）依赖这个行为。
     * <p>
     * 调用前提：入参必须是先 {@link #findById(Long)} 拿到的完整 DTO，改动要改的字段后
     * 原样回传其余字段；如果直接 new 一个只填了部分字段的 DTO 调用本方法，未填的字段会
     * 被当作「显式改成 null/默认值」真的写进库——例如漏填 {@code landNodeId} 会把已分配的
     * 落地出口静默清空，漏填 {@code status} 会被重置成默认值 {@code ACTIVE}。这与
     * {@link ProxyNodeRepository#update} 的「null 字段跳过」语义相反，调用方不要搞混。
     */
    void update(UserDto user);

    void deleteById(Long id);

    boolean existsBySubject(String subject);

    boolean existsByFrontNodeId(Long nodeId);
}
