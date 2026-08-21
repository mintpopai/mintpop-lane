package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.converter.NodeGroupConverter;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.entity.NodeGroup;
import ai.mintpop.lane.mapper.NodeGroupMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 节点分组的 MySQL 实现。 */
@Repository
public class MybatisNodeGroupRepository implements NodeGroupRepository {

    private final NodeGroupMapper mapper;
    private final NodeGroupConverter converter;

    public MybatisNodeGroupRepository(NodeGroupMapper mapper, NodeGroupConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<NodeGroupDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id)).map(converter::toDto);
    }

    @Override
    public List<NodeGroupDto> findAll() {
        return mapper.selectList(Wrappers.<NodeGroup>lambdaQuery().orderByAsc(NodeGroup::getId))
                .stream().map(converter::toDto).toList();
    }

    @Override
    public Long create(NodeGroupDto group) {
        NodeGroup entity = converter.toEntity(group);
        entity.setId(null);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(NodeGroupDto group) {
        mapper.updateById(converter.toEntity(group));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.selectCount(Wrappers.<NodeGroup>lambdaQuery().eq(NodeGroup::getName, name)) > 0;
    }
}
