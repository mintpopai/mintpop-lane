package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.SubscriptionDto;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 订阅的读写口。常规读写（find / create / update）一律是明文 DTO，密文与 MyBatis-Plus
 * 都被挡在实现之下；但 {@link #updateCredential} / {@link #clearCredentialMetadata}
 * 是写路径上有意为之的例外——它们直接收/清凭证密文，由调用方（CredentialIssueServiceImpl）
 * 自行完成加密，不经由本仓储做明文转换。
 */
public interface SubscriptionRepository {

    Optional<SubscriptionDto> findById(Long id);

    /** 某用户的全部订阅（含已过期），按 id 升序 */
    List<SubscriptionDto> findByUserId(Long userId);

    /** 批量取多个用户的全部订阅，供管理端列表拼摘要，避免逐行查询 */
    List<SubscriptionDto> findByUserIds(Collection<Long> userIds);

    /** 新建，返回自增主键 */
    Long create(SubscriptionDto subscription);

    /**
     * 按 id 更新全部可变字段。null 会被显式写库；沿用/清除策略由调用方决定
     * （当前管理端语义为留空沿用——调用前先 findById 拿完整 DTO，字段留空时原样回填，
     * 而非传 null 靠本方法清空），调用前提同 UserRepository.update：
     * 必须回传 findById 拿到的完整 DTO。
     */
    void update(SubscriptionDto subscription);

    void deleteById(Long id);

    /** 是否还有订阅归属于该企业。企业删除前的引用检查用，企业侧不设外键，靠这里把关 */
    boolean existsByEnterpriseId(Long enterpriseId);

    /**
     * 一次性写入凭证密文与其全部签发元数据。与 {@link #update} 不同，
     * 本方法只动这六列，不走整条 DTO 覆盖那条路——避免把签发流程之外、
     * 调用方手上未必持有的其它字段（如 name、remark）连带覆盖成旧快照。
     * refreshCipher 可为 null（服务端未下发 refresh_token 时）。
     */
    void updateCredential(Long subscriptionId,
                          String credentialCipher,
                          String scope,
                          String tokenUuid,
                          Instant issuedAt,
                          Instant expiresAt,
                          String refreshCipher);

    /**
     * 清空凭证的全部签发元数据（scope/tokenUuid/issuedAt/expiresAt/refreshCipher），
     * 凭证密文本身不动。手工录入凭证时调用——手工凭证来源不明、有效期未知，
     * 继承上一次签发的元数据会让后台显示错误的到期日。与 {@link #updateCredential}
     * 对称：那个负责写入，这个负责清空，都绕开常规 {@link #update}，
     * 不占用常规更新路径的 SQL 列，避免把这五列纳入日常更新引入静默覆盖风险。
     */
    void clearCredentialMetadata(Long subscriptionId);
}
