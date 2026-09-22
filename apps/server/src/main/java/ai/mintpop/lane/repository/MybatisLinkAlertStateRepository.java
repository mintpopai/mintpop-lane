package ai.mintpop.lane.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import ai.mintpop.lane.entity.LinkAlertState;
import ai.mintpop.lane.mapper.LinkAlertStateMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 链路告警去重状态的 MySQL 实现。 */
@Repository
public class MybatisLinkAlertStateRepository implements LinkAlertStateRepository {

    private final LinkAlertStateMapper mapper;

    public MybatisLinkAlertStateRepository(LinkAlertStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<LinkAlertState> find(Long userId, String failureDomain, String asn) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<LinkAlertState>lambdaQuery()
                .eq(LinkAlertState::getUserId, userId)
                .eq(LinkAlertState::getFailureDomain, failureDomain)
                .eq(LinkAlertState::getAsn, asn)));
    }

    @Override
    public void upsert(LinkAlertState state) {
        mapper.upsert(state);
    }
}
