package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.UserFrontSubscription;
import ai.mintpop.lane.mapper.UserFrontSubscriptionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户第一跳机场订阅列表的 MySQL 实现 */
@Repository
public class MybatisUserFrontSubscriptionRepository implements UserFrontSubscriptionRepository {

    private final UserFrontSubscriptionMapper mapper;

    public MybatisUserFrontSubscriptionRepository(UserFrontSubscriptionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Long> findSubscriptionIdsByUserId(Long userId) {
        return mapper.selectList(Wrappers.<UserFrontSubscription>lambdaQuery()
                        .eq(UserFrontSubscription::getUserId, userId)
                        .orderByAsc(UserFrontSubscription::getPosition))
                .stream().map(UserFrontSubscription::getAirportSubscriptionId).toList();
    }

    @Override
    public Map<Long, List<Long>> findSubscriptionIdsByUserIds(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return group(mapper.selectList(Wrappers.<UserFrontSubscription>lambdaQuery()
                .in(UserFrontSubscription::getUserId, userIds)
                .orderByAsc(UserFrontSubscription::getUserId, UserFrontSubscription::getPosition)));
    }

    @Override
    public Map<Long, List<Long>> findAllGroupedByUser() {
        return group(mapper.selectList(Wrappers.<UserFrontSubscription>lambdaQuery()
                .orderByAsc(UserFrontSubscription::getUserId, UserFrontSubscription::getPosition)));
    }

    @Override
    public void replaceForUser(Long userId, List<Long> airportSubscriptionIdsInOrder) {
        deleteByUserId(userId);
        for (int position = 0; position < airportSubscriptionIdsInOrder.size(); position++) {
            UserFrontSubscription row = new UserFrontSubscription();
            row.setUserId(userId);
            row.setPosition(position);
            row.setAirportSubscriptionId(airportSubscriptionIdsInOrder.get(position));
            mapper.insert(row);
        }
    }

    @Override
    public void deleteByUserId(Long userId) {
        mapper.delete(Wrappers.<UserFrontSubscription>lambdaQuery().eq(UserFrontSubscription::getUserId, userId));
    }

    @Override
    public boolean existsByAirportSubscriptionId(Long airportSubscriptionId) {
        return mapper.selectCount(Wrappers.<UserFrontSubscription>lambdaQuery()
                .eq(UserFrontSubscription::getAirportSubscriptionId, airportSubscriptionId)) > 0;
    }

    @Override
    public Map<Long, Long> countPrimaryByAirportSubscription() {
        return mapper.selectList(Wrappers.<UserFrontSubscription>lambdaQuery()
                        .eq(UserFrontSubscription::getPosition, 0))
                .stream()
                .collect(Collectors.groupingBy(UserFrontSubscription::getAirportSubscriptionId, Collectors.counting()));
    }

    /** 已按 user_id、position 排好序的行，按用户分组并保持顺位 */
    private static Map<Long, List<Long>> group(List<UserFrontSubscription> rows) {
        return rows.stream().collect(Collectors.groupingBy(UserFrontSubscription::getUserId, LinkedHashMap::new,
                Collectors.mapping(UserFrontSubscription::getAirportSubscriptionId, Collectors.toList())));
    }
}
