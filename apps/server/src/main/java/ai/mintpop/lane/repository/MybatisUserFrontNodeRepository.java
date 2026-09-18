package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.UserFrontNode;
import ai.mintpop.lane.mapper.UserFrontNodeMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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

    @Override
    public List<Long> findNodeIdsByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserFrontNode>lambdaQuery().eq(UserFrontNode::getUserId, userId))
                .stream().map(UserFrontNode::getNodeId).toList();
    }

    @Override
    public Map<Long, List<Long>> findNodeIdsByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(Wrappers.<UserFrontNode>lambdaQuery().in(UserFrontNode::getUserId, userIds))
                .stream()
                .collect(Collectors.groupingBy(UserFrontNode::getUserId,
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
