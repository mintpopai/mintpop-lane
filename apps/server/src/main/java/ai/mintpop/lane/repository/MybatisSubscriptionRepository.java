package ai.mintpop.lane.repository;

import ai.mintpop.lane.converter.SubscriptionConverter;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.entity.Subscription;
import ai.mintpop.lane.mapper.SubscriptionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 订阅的 MySQL 实现。 */
@Repository
public class MybatisSubscriptionRepository implements SubscriptionRepository {

    private final SubscriptionMapper mapper;
    private final SubscriptionConverter converter;

    public MybatisSubscriptionRepository(SubscriptionMapper mapper, SubscriptionConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<SubscriptionDto> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id)).map(converter::toDto);
    }

    @Override
    public List<SubscriptionDto> findByUserId(Long userId) {
        return mapper.selectList(Wrappers.<Subscription>lambdaQuery()
                        .eq(Subscription::getUserId, userId)
                        .orderByAsc(Subscription::getId))
                .stream().map(converter::toDto).toList();
    }

    @Override
    public List<SubscriptionDto> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<Subscription>lambdaQuery()
                        .in(Subscription::getUserId, userIds)
                        .orderByAsc(Subscription::getId))
                .stream().map(converter::toDto).toList();
    }

    @Override
    public Long create(SubscriptionDto subscription) {
        Subscription entity = converter.toEntity(subscription);
        entity.setId(null);
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(SubscriptionDto subscription) {
        Subscription entity = converter.toEntity(subscription);
        // 逐列 set：null 也要显式写库（清除凭据依赖这个行为），不能用默认跳过 null 的 updateById
        mapper.update(null, Wrappers.<Subscription>lambdaUpdate()
                .eq(Subscription::getId, subscription.getId())
                .set(Subscription::getEnterpriseId, entity.getEnterpriseId())
                .set(Subscription::getAgentType, entity.getAgentType())
                .set(Subscription::getName, entity.getName())
                .set(Subscription::getStartsAt, entity.getStartsAt())
                .set(Subscription::getEndsAt, entity.getEndsAt())
                .set(Subscription::getAccountEmail, entity.getAccountEmail())
                .set(Subscription::getCredentialCipher, entity.getCredentialCipher())
                .set(Subscription::getRemark, entity.getRemark()));
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public boolean existsByEnterpriseId(Long enterpriseId) {
        if (enterpriseId == null) {
            return false;
        }
        return mapper.selectCount(Wrappers.<Subscription>lambdaQuery()
                .eq(Subscription::getEnterpriseId, enterpriseId)) > 0;
    }

    @Override
    public void updateCredential(Long subscriptionId,
                                 String credentialCipher,
                                 String scope,
                                 String tokenUuid,
                                 Instant issuedAt,
                                 Instant expiresAt,
                                 String refreshCipher) {
        mapper.update(null, Wrappers.<Subscription>lambdaUpdate()
                .eq(Subscription::getId, subscriptionId)
                .set(Subscription::getCredentialCipher, credentialCipher)
                .set(Subscription::getCredentialScope, scope)
                .set(Subscription::getCredentialTokenUuid, tokenUuid)
                .set(Subscription::getCredentialIssuedAt, issuedAt)
                .set(Subscription::getCredentialExpiresAt, expiresAt)
                .set(Subscription::getCredentialRefreshCipher, refreshCipher));
    }

    @Override
    public void clearCredentialMetadata(Long subscriptionId) {
        // 与 updateCredential 对称：那个写入五列元数据，这个清空同样五列，凭证密文本身不动
        mapper.update(null, Wrappers.<Subscription>lambdaUpdate()
                .eq(Subscription::getId, subscriptionId)
                .set(Subscription::getCredentialScope, null)
                .set(Subscription::getCredentialTokenUuid, null)
                .set(Subscription::getCredentialIssuedAt, null)
                .set(Subscription::getCredentialExpiresAt, null)
                .set(Subscription::getCredentialRefreshCipher, null));
    }

    @Override
    public void clearCredential(Long subscriptionId) {
        // 与 clearCredentialMetadata 的区别只在多清 credentialCipher 这一列：吊销要连凭证密文本身一起清掉
        mapper.update(null, Wrappers.<Subscription>lambdaUpdate()
                .eq(Subscription::getId, subscriptionId)
                .set(Subscription::getCredentialCipher, null)
                .set(Subscription::getCredentialScope, null)
                .set(Subscription::getCredentialTokenUuid, null)
                .set(Subscription::getCredentialIssuedAt, null)
                .set(Subscription::getCredentialExpiresAt, null)
                .set(Subscription::getCredentialRefreshCipher, null));
    }
}
