package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.Plan;
import ai.mintpop.lane.mapper.PlanMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 套餐的 MySQL 实现。 */
@Repository
public class MybatisPlanRepository implements PlanRepository {

    private final PlanMapper mapper;

    public MybatisPlanRepository(PlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Plan> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<Plan> findAll() {
        return mapper.selectList(Wrappers.<Plan>lambdaQuery().orderByAsc(Plan::getId));
    }

    @Override
    public Long create(Plan plan) {
        plan.setId(null);
        mapper.insert(plan);
        return plan.getId();
    }

    @Override
    public void update(Plan plan) {
        mapper.updateById(plan);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.selectCount(Wrappers.<Plan>lambdaQuery().eq(Plan::getName, name)) > 0;
    }

    @Override
    public boolean existsByNameExcludingId(String name, Long excludeId) {
        return mapper.selectCount(Wrappers.<Plan>lambdaQuery()
                .eq(Plan::getName, name).ne(Plan::getId, excludeId)) > 0;
    }
}
