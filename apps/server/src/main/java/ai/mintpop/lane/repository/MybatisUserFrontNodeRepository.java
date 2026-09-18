package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.UserFrontNode;
import ai.mintpop.lane.mapper.UserFrontNodeMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户前置节点集合的 MySQL 实现。 */
@Repository
public class MybatisUserFrontNodeRepository implements UserFrontNodeRepository {

    private final UserFrontNodeMapper mapper;

    public MybatisUserFrontNodeRepository(UserFrontNodeMapper mapper) {
        this.mapper = mapper;
    }

    /*
     * 两个查询都必须 ORDER BY id：replaceForUser 是按分配器的排名（负载升序、同负载按 id）
     * 逐条插入的，自增主键忠实记录了这个顺序，取回时只有按它排才还原得出来。不写 ORDER BY
     * 时 MySQL 多半会按 uk_user_front_node(user_id, node_id) 索引返回，也就是按 node_id 排
     * ——本来就是未定义行为。后果不止是「顺序不好看」：下发的 frontGroups[0].nodes[0] 会变成
     * 组内 id 最小的节点，而 front_node_id 是负载最低的那个，两者只在巧合时相同；于是管理端
     * 显示的与客户端实际首选的不是同一个，「按已分配用户数最少优先摊平负载」这条设计也在
     * 真正决定流量落点的首选位上被 id 顺序覆盖掉；顺序未定义还意味着 front 可能在两次请求
     * 之间跳变，老客户端跟着换节点重握手。
     */

    @Override
    public List<Long> findNodeIdsByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserFrontNode>lambdaQuery()
                        .eq(UserFrontNode::getUserId, userId)
                        .orderByAsc(UserFrontNode::getId))
                .stream().map(UserFrontNode::getNodeId).toList();
    }

    @Override
    public Map<Long, List<Long>> findNodeIdsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(Wrappers.<UserFrontNode>lambdaQuery()
                        .in(UserFrontNode::getUserId, userIds)
                        .orderByAsc(UserFrontNode::getId))
                .stream()
                .collect(Collectors.groupingBy(UserFrontNode::getUserId, LinkedHashMap::new,
                        Collectors.mapping(UserFrontNode::getNodeId, Collectors.toList())));
    }

    @Override
    @Transactional
    public void replaceForUser(Long userId, List<Long> nodeIds) {
        mapper.delete(Wrappers.<UserFrontNode>lambdaQuery().eq(UserFrontNode::getUserId, userId));
        nodeIds.stream().distinct().forEach(nodeId -> {
            UserFrontNode entity = new UserFrontNode();
            entity.setUserId(userId);
            entity.setNodeId(nodeId);
            mapper.insert(entity);
        });
    }

    @Override
    public void deleteByUserId(Long userId) {
        mapper.delete(Wrappers.<UserFrontNode>lambdaQuery().eq(UserFrontNode::getUserId, userId));
    }

    @Override
    public Map<Long, Long> countUsersByNodeId() {
        // 表规模是「用户数 × 每用户节点数」量级，全量取出在 Java 侧 GROUP BY 足够快，
        // 且避免了 MyBatis-Plus selectMaps 在不同数据库驱动下列名大小写不一致的坑
        return mapper.selectList(Wrappers.<UserFrontNode>lambdaQuery()).stream()
                .collect(Collectors.groupingBy(UserFrontNode::getNodeId, Collectors.counting()));
    }

    @Override
    public boolean existsByNodeId(Long nodeId) {
        return mapper.selectCount(Wrappers.<UserFrontNode>lambdaQuery().eq(UserFrontNode::getNodeId, nodeId)) > 0;
    }
}
