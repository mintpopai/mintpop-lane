package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.Enterprise;
import ai.mintpop.lane.mapper.EnterpriseMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 企业的 MySQL 实现。 */
@Repository
public class MybatisEnterpriseRepository implements EnterpriseRepository {

    private final EnterpriseMapper mapper;

    public MybatisEnterpriseRepository(EnterpriseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Enterprise> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<Enterprise> findAll() {
        return mapper.selectList(Wrappers.<Enterprise>lambdaQuery().orderByAsc(Enterprise::getId));
    }

    @Override
    public Long create(Enterprise enterprise) {
        enterprise.setId(null);
        mapper.insert(enterprise);
        return enterprise.getId();
    }

    @Override
    public void update(Enterprise enterprise) {
        mapper.updateById(enterprise);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.selectCount(Wrappers.<Enterprise>lambdaQuery().eq(Enterprise::getName, name)) > 0;
    }

    @Override
    public boolean existsByDomain(String domain) {
        return mapper.selectCount(Wrappers.<Enterprise>lambdaQuery().eq(Enterprise::getDomain, domain)) > 0;
    }

    @Override
    public boolean existsByNameExcludingId(String name, Long excludeId) {
        return mapper.selectCount(Wrappers.<Enterprise>lambdaQuery()
                .eq(Enterprise::getName, name).ne(Enterprise::getId, excludeId)) > 0;
    }

    @Override
    public boolean existsByDomainExcludingId(String domain, Long excludeId) {
        return mapper.selectCount(Wrappers.<Enterprise>lambdaQuery()
                .eq(Enterprise::getDomain, domain).ne(Enterprise::getId, excludeId)) > 0;
    }
}
