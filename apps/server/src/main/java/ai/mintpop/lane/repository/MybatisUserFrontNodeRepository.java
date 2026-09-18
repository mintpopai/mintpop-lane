package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.UserFrontNode;
import ai.mintpop.lane.mapper.UserFrontNodeMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}
