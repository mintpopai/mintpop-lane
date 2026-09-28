package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.converter.AirportSubscriptionConverter;
import ai.mintpop.lane.dto.AirportSubscriptionDto;
import ai.mintpop.lane.entity.AirportSubscription;
import ai.mintpop.lane.mapper.AirportSubscriptionMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 机场订阅的 MySQL 实现。 */
@Repository
public class MybatisAirportSubscriptionRepository implements AirportSubscriptionRepository {

    private final AirportSubscriptionMapper mapper;
    private final AirportSubscriptionConverter converter;

    public MybatisAirportSubscriptionRepository(AirportSubscriptionMapper mapper, AirportSubscriptionConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<AirportSubscriptionDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id)).map(converter::toDto);
    }

    @Override
    public List<AirportSubscriptionDto> findAll() {
        return mapper.selectList(Wrappers.<AirportSubscription>lambdaQuery().orderByAsc(AirportSubscription::getId))
                .stream().map(converter::toDto).toList();
    }

    @Override
    public Long create(AirportSubscriptionDto group) {
        AirportSubscription entity = converter.toEntity(group);
        entity.setId(null);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(AirportSubscriptionDto group) {
        mapper.updateById(converter.toEntity(group));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return mapper.selectCount(Wrappers.<AirportSubscription>lambdaQuery().eq(AirportSubscription::getName, name)) > 0;
    }

    @Override
    public boolean existsByNameExcludingId(String name, Long excludeId) {
        return mapper.selectCount(Wrappers.<AirportSubscription>lambdaQuery()
                .eq(AirportSubscription::getName, name).ne(AirportSubscription::getId, excludeId)) > 0;
    }

    @Override
    public boolean existsByAirportId(Long airportId) {
        return mapper.selectCount(Wrappers.<AirportSubscription>lambdaQuery()
                .eq(AirportSubscription::getAirportId, airportId)) > 0;
    }

    @Override
    public List<AirportSubscriptionDto> findAllForUpdate() {
        return mapper.selectList(Wrappers.<AirportSubscription>lambdaQuery()
                        .orderByAsc(AirportSubscription::getId)
                        .last("FOR UPDATE"))
                .stream().map(converter::toDto).toList();
    }
}
