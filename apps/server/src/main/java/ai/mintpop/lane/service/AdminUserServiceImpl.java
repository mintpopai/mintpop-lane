package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.PageResult;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.UserSaveRequest;
import ai.mintpop.lane.response.AdminUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public AdminUserServiceImpl(UserRepository userRepository, ProxyNodeRepository nodeRepository,
                                 SubscriptionRepository subscriptionRepository, Clock clock) {
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    public PageResult<AdminUserResponse> page(String keyword, Boolean hasActiveSubscription,
                                              long pageNo, long pageSize) {
        PageResult<UserDto> page = userRepository.search(keyword, hasActiveSubscription, pageNo, pageSize);
        Map<Long, ProxyNodeDto> nodes = nodeRepository.findAll(null).stream()
                .collect(Collectors.toMap(ProxyNodeDto::getId, Function.identity()));

        // 一次取回本页所有用户的订阅，避免逐行查询
        List<Long> userIds = page.records().stream().map(UserDto::getId).toList();
        Instant now = clock.instant();
        Map<Long, List<AdminUserResponse.ActiveSubscriptionBrief>> briefs =
                subscriptionRepository.findByUserIds(userIds).stream()
                        .filter(s -> s.isActiveAt(now))
                        .collect(Collectors.groupingBy(SubscriptionDto::getUserId,
                                Collectors.mapping(s -> new AdminUserResponse.ActiveSubscriptionBrief(
                                        s.getId(), s.getName(), s.getAgentType(), s.getEndsAt()),
                                        Collectors.toList())));

        List<AdminUserResponse> records = page.records().stream()
                .map(user -> toResponse(user, nodes, briefs.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(records, page.total(), page.pageNo(), page.pageSize());
    }

    /**
     * 容量校验与写入必须同事务：validateLandAvailable 里的节点行锁把同一节点的
     * 并发分配串行化，锁要一直握到 update 落库提交。隔离级别用 READ_COMMITTED——
     * MySQL 默认的 REPEATABLE READ 下，等锁归来后的普通读仍用事务开头的旧快照，
     * 会看不见等待期间别人刚提交的绑定，容量统计照旧超卖；READ_COMMITTED 每条语句
     * 取新快照，拿到锁后统计到的就是最新已提交的绑定数。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(Long id, UserSaveRequest request) {
        UserDto user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));

        if (request.getFrontNodeId() != null) {
            validateNode(request.getFrontNodeId(), NodeRole.FRONT);
        }
        validateLandAvailable(request.getLandNodeId(), id);

        user.setStatus(request.getStatus());
        user.setFrontNodeId(request.getFrontNodeId());
        user.setLandNodeId(request.getLandNodeId());
        // subject/email/role 不从入参取，沿用库里的值（邮箱是身份标识，由登录同步维护，管理端不提供改动入口）

        userRepository.update(user);
    }

    @Override
    public void delete(Long id) {
        userRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        userRepository.deleteById(id);
    }

    private void validateNode(Long nodeId, NodeRole expectedRole) {
        ProxyNodeDto node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));
        if (node.getRole() != expectedRole) {
            throw new BizException(BizCodeEnum.NODE_ROLE_MISMATCH);
        }
    }

    /**
     * 落地节点必须存在、角色正确，且还有剩余容量（除本人外的绑定人数 < capacity）。
     * 用锁定读取节点行（见 {@link ProxyNodeRepository#findByIdForUpdate}），
     * 同一节点的并发分配在此串行化，配合外层事务的 READ_COMMITTED 防止超卖。
     * <p>
     * 计数按「排除本人」统计，而不是「重存同一节点直接放行」的快速路径：
     * 后者依赖加锁前读到的用户旧快照，在「读快照 → 拿到锁」的间隙里若发生
     * 「先解绑本人、再由别人占走最后一个名额」，凭旧快照放行会把绑定人数写超容量；
     * 排除本人的计数在拿到锁之后才执行（READ_COMMITTED 下读到的是最新已提交状态），
     * 重存同一节点天然不新占名额，也顺带修掉了并发重复提交时的 410016 误报。
     */
    private void validateLandAvailable(Long landNodeId, Long userId) {
        if (landNodeId == null) {
            return;
        }
        ProxyNodeDto node = nodeRepository.findByIdForUpdate(landNodeId)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));
        if (node.getRole() != NodeRole.LAND) {
            throw new BizException(BizCodeEnum.NODE_ROLE_MISMATCH);
        }
        if (userRepository.countByLandNodeIdExcludingUser(landNodeId, userId) >= node.getCapacity()) {
            throw new BizException(BizCodeEnum.LAND_NODE_FULL);
        }
    }

    private AdminUserResponse toResponse(UserDto user, Map<Long, ProxyNodeDto> nodes,
                                         List<AdminUserResponse.ActiveSubscriptionBrief> activeSubscriptions) {
        ProxyNodeDto front = nodes.get(user.getFrontNodeId());
        ProxyNodeDto land = user.getLandNodeId() == null ? null : nodes.get(user.getLandNodeId());

        return new AdminUserResponse(
                user.getId(),
                user.getSubject(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getFrontNodeId(),
                front == null ? null : front.getName(),
                user.getLandNodeId(),
                land == null ? null : land.getName(),
                land == null ? null : land.getEgressIp(),
                activeSubscriptions,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
