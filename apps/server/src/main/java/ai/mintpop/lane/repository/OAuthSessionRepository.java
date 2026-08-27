package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.OAuthSession;

import java.time.Instant;
import java.util.Optional;

/**
 * 授权会话的读写口。会话只在「生成授权链接」到「兑换凭证」之间存活，
 * 兑换成功或超时即删除，故直接操作 entity，不设 DTO/converter。
 */
public interface OAuthSessionRepository {

    void save(OAuthSession session);

    Optional<OAuthSession> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    /** 清理逾期未兑换的会话，供后续维护任务调用 */
    int deleteExpiredBefore(Instant deadline);
}
