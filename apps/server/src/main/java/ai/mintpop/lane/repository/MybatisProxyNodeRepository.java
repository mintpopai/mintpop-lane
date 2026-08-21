package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.converter.ProxyNodeConverter;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.entity.ProxyNode;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.mapper.ProxyNodeMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 节点池的 MySQL 实现。 */
@Repository
public class MybatisProxyNodeRepository implements ProxyNodeRepository {

    private final ProxyNodeMapper mapper;
    private final ProxyNodeConverter converter;

    public MybatisProxyNodeRepository(ProxyNodeMapper mapper, ProxyNodeConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<ProxyNodeDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id)).map(converter::toDto);
    }

    @Override
    public List<ProxyNodeDto> findAll(NodeRole role) {
        var query = Wrappers.<ProxyNode>lambdaQuery().orderByAsc(ProxyNode::getId);
        if (role != null) {
            query.eq(ProxyNode::getRole, role);
        }
        return mapper.selectList(query).stream().map(converter::toDto).toList();
    }

    @Override
    public Long create(ProxyNodeDto node) {
        ProxyNode entity = converter.toEntity(node);
        entity.setId(null);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(ProxyNodeDto node) {
        // 走实体更新（而非 update wrapper 的逐列 set）：JSON 列的 typeHandler 只在
        // 实体方式下生效，用 set() 会把 Map 当普通参数写进去
        mapper.updateById(converter.toEntity(node));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.selectCount(Wrappers.<ProxyNode>lambdaQuery().eq(ProxyNode::getName, name)) > 0;
    }

    @Override
    public Optional<ProxyNodeDto> findByGroupIdAndSourceName(Long groupId, String sourceName) {
        return mapper.selectList(Wrappers.<ProxyNode>lambdaQuery()
                        .eq(ProxyNode::getGroupId, groupId).eq(ProxyNode::getSourceName, sourceName))
                .stream().findFirst().map(converter::toDto);
    }

    @Override
    public List<ProxyNodeDto> findByGroupId(Long groupId) {
        return mapper.selectList(Wrappers.<ProxyNode>lambdaQuery()
                        .eq(ProxyNode::getGroupId, groupId).orderByAsc(ProxyNode::getId))
                .stream().map(converter::toDto).toList();
    }

    @Override
    public long countByGroupId(Long groupId) {
        return mapper.selectCount(Wrappers.<ProxyNode>lambdaQuery().eq(ProxyNode::getGroupId, groupId));
    }
}
