package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.OAuthSession;
import ai.mintpop.lane.mapper.OAuthSessionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** 授权会话的 MySQL 实现。 */
@Repository
public class MybatisOAuthSessionRepository implements OAuthSessionRepository {

    private final OAuthSessionMapper mapper;

    public MybatisOAuthSessionRepository(OAuthSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(OAuthSession session) {
        session.setId(null);
        mapper.insert(session);
    }

    @Override
    public Optional<OAuthSession> findBySessionId(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(Wrappers.<OAuthSession>lambdaQuery()
                .eq(OAuthSession::getSessionId, sessionId)));
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        mapper.delete(Wrappers.<OAuthSession>lambdaQuery()
                .eq(OAuthSession::getSessionId, sessionId));
    }

    @Override
    public int deleteExpiredBefore(Instant deadline) {
        return mapper.delete(Wrappers.<OAuthSession>lambdaQuery()
                .lt(OAuthSession::getExpiresAt, deadline));
    }
}
