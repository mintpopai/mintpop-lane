package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.Airport;
import ai.mintpop.lane.mapper.AirportMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 机场的 MySQL 实现 */
@Repository
public class MybatisAirportRepository implements AirportRepository {

    private final AirportMapper mapper;

    public MybatisAirportRepository(AirportMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Airport> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<Airport> findAll() {
        return mapper.selectList(Wrappers.<Airport>lambdaQuery().orderByAsc(Airport::getId));
    }

    @Override
    public Long create(Airport airport) {
        airport.setId(null);
        mapper.insert(airport);
        return airport.getId();
    }

    @Override
    public void update(Airport airport) {
        mapper.updateById(airport);
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.selectCount(Wrappers.<Airport>lambdaQuery().eq(Airport::getName, name)) > 0;
    }

    @Override
    public boolean existsByNameExcludingId(String name, Long excludeId) {
        return mapper.selectCount(Wrappers.<Airport>lambdaQuery()
                .eq(Airport::getName, name).ne(Airport::getId, excludeId)) > 0;
    }
}
