package ai.mintpop.lane.repository;

import ai.mintpop.lane.dto.SubscriptionDto;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 订阅的读写口。返回的一律是明文 DTO，密文与 MyBatis-Plus 都被挡在实现之下。 */
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
}
