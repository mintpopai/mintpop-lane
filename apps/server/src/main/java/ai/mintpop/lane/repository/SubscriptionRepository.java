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
     * 写入席位账号的组织身份（凭证签发后从 profile 取得）。
     * 不并进 {@link #updateCredential} 是因为它来自<b>另一次</b>上游调用、且允许缺失：
     * profile 拉不到不该让已经到手的凭证连带作废，此时这两列留空，
     * 客户端按空串跳过 Fable 计费同意的预置、退回弹窗。
     *
     * @param orgUuid           组织 UUID，客户端据此预置 Fable 计费同意
     * @param extraUsageEnabled 该组织是否已开启 usage credits；false 时预置不生效，管理端应提示
     */
    void updateCredentialOrg(Long subscriptionId, String orgUuid, Boolean extraUsageEnabled);

    /**
     * 清空凭证的全部签发元数据（scope/tokenUuid/issuedAt/expiresAt/refreshCipher
     * 与组织身份两列），凭证密文本身不动。手工录入凭证时调用——手工凭证来源不明、有效期未知，
     * 继承上一次签发的元数据会让后台显示错误的到期日。与 {@link #updateCredential}
     * 对称：那个负责写入，这个负责清空，都绕开常规 {@link #update}，
     * 不占用常规更新路径的 SQL 列，避免把这几列纳入日常更新引入静默覆盖风险。
     */
    void clearCredentialMetadata(Long subscriptionId);

    /**
     * 清空凭证密文本身与其全部签发元数据（credentialCipher 连同 {@link #clearCredentialMetadata}
     * 清的那五列一并置空）。用于吊销：管理员点吊销的意图是「这个席位不该再有凭证」，
     * 无论上游吊销是否成功，本地都不应继续留着它、也不应继续下发给客户端。
     * 与 {@link #clearCredentialMetadata} 的区别只在多清 credentialCipher 这一列，
     * 同样绕开常规 {@link #update}，不占用常规更新路径的 SQL 列。
     */
    void clearCredential(Long subscriptionId);

    /**
     * 首次绑定：**仅当当前未绑定**才写入，返回是否生效。
     * 两台设备同时对同一份未绑定订阅点绑定时，靠 WHERE bound_device_id IS NULL 决出胜负，
     * 绝不先查后写——那中间的窗口正好够第二台设备也判定成「未绑定」。
     */
    boolean bindDeviceIfUnbound(Long subscriptionId, Long deviceId, Instant now);

    /** 管理员同意换机后的改绑：无条件覆盖（裁决本身已由申请的条件 UPDATE 串行化） */
    void rebindDevice(Long subscriptionId, Long deviceId, Instant now);

    /** 管理员强制解绑：两列一起清空 */
    void unbindDevice(Long subscriptionId);
}
