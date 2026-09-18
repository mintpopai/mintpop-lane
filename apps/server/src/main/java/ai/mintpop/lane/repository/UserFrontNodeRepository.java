package ai.mintpop.lane.repository;

import java.util.List;
import java.util.Map;

/**
 * 用户前置节点集合的读写口：一个用户对应一组前置节点，
 * 供客户端在其中做故障转移。上层只依赖这个接口，看不到 MyBatis-Plus。
 */
public interface UserFrontNodeRepository {

    /** 该用户当前的前置节点 id 集合；未分配时返回空列表 */
    List<Long> findNodeIdsByUserId(Long userId);

    /** 整体替换该用户的前置节点集合：先清空该用户原有记录再写入，入参按 id 去重 */
    void replaceForUser(Long userId, List<Long> nodeIds);

    /** 清空该用户的全部前置节点记录 */
    void deleteByUserId(Long userId);

    /**
     * 按前置节点 id 统计已分配的用户数（GROUP BY node_id）。
     * 分配算法据此摊平负载：没有任何用户绑定的节点不出现在返回的 Map 里，
     * 调用方按 {@code getOrDefault(id, 0L)} 取值。
     */
    Map<Long, Long> countUsersByNodeId();
}
