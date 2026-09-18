package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.PageResult;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.SubscriptionDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.enumeration.UserRole;
import ai.mintpop.lane.enumeration.UserStatus;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.SubscriptionRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
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
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserFrontNodeRepository userFrontNodeRepository;
    private final FrontNodeAllocator frontNodeAllocator;
    private final Clock clock;

    public AdminUserServiceImpl(UserRepository userRepository, ProxyNodeRepository nodeRepository,
                                 SubscriptionRepository subscriptionRepository,
                                 UserFrontNodeRepository userFrontNodeRepository,
                                 FrontNodeAllocator frontNodeAllocator, Clock clock) {
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userFrontNodeRepository = userFrontNodeRepository;
        this.frontNodeAllocator = frontNodeAllocator;
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
        // 同上，前置节点也一次取回本页所有用户的，避免逐行查询
        // （UsersView 列表页目前虽不展示 frontNodes/failureDomainCount，但 toResponse 是
        // page()/get() 共用的同一份组装逻辑，响应形状必须一致，不能靠「列表页不查」取巧）
        Map<Long, List<Long>> frontNodeIdsByUser = userFrontNodeRepository.findNodeIdsByUserIds(userIds);

        List<AdminUserResponse> records = page.records().stream()
                .map(user -> toResponse(user, nodes, briefs.getOrDefault(user.getId(), List.of()),
                        frontNodeIdsByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return new PageResult<>(records, page.total(), page.pageNo(), page.pageSize());
    }

    @Override
    public AdminUserResponse get(Long id) {
        UserDto user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        Map<Long, ProxyNodeDto> nodes = nodeRepository.findAll(null).stream()
                .collect(Collectors.toMap(ProxyNodeDto::getId, Function.identity()));
        Instant now = clock.instant();
        List<AdminUserResponse.ActiveSubscriptionBrief> briefs = subscriptionRepository.findByUserId(id).stream()
                .filter(s -> s.isActiveAt(now))
                .map(s -> new AdminUserResponse.ActiveSubscriptionBrief(
                        s.getId(), s.getName(), s.getAgentType(), s.getEndsAt()))
                .toList();
        List<Long> frontNodeIds = userFrontNodeRepository.findNodeIdsByUserId(id);
        return toResponse(user, nodes, briefs, frontNodeIds);
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
        // 管理员账号受保护：处置态（停用/吊销）一律拒绝，只放行保持 ACTIVE 的资源分配。
        // 管理员是进入管理端的唯一钥匙，处置掉最后一个管理员等于把自己锁在门外。
        if (user.getRole() == UserRole.ADMIN && request.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(BizCodeEnum.ADMIN_USER_PROTECTED);
        }

        FrontAssignment front = resolveFrontAssignment(id, user, request);
        validateLandAvailable(request.getLandNodeId(), id);

        user.setStatus(request.getStatus());
        user.setFrontNodeId(front.primaryNodeId());
        user.setLandNodeId(request.getLandNodeId());
        user.setRemark(request.getRemark());
        // subject/email/role 不从入参取，沿用库里的值（邮箱是身份标识，由登录同步维护，管理端不提供改动入口）

        userRepository.update(user);
        switch (front.write()) {
            // 「不碰」与「清空」是两种截然不同的处置，分成两个枚举值而不是让空列表兼职表达
            case KEEP -> { }
            case CLEAR -> userFrontNodeRepository.deleteByUserId(id);
            case REPLACE -> userFrontNodeRepository.replaceForUser(id, front.nodeIds());
        }
    }

    /** 本次保存要对 user_front_node 做什么 */
    private enum FrontGroupWrite {
        /** 本次保存没有动第一跳，关联表原样不碰 */
        KEEP,
        /** 按算出来的节点集合整体替换 */
        REPLACE,
        /** 清空该用户的前置节点组 */
        CLEAR
    }

    /**
     * 本次保存对前置节点的处置：写回 {@code front_node_id} 的主节点 + 对 user_front_node 的动作。
     * {@code nodeIds} 只在 {@link FrontGroupWrite#REPLACE} 下有意义。
     */
    private record FrontAssignment(FrontGroupWrite write, Long primaryNodeId, List<Long> nodeIds) {

        static FrontAssignment keep(Long primaryNodeId) {
            return new FrontAssignment(FrontGroupWrite.KEEP, primaryNodeId, List.of());
        }

        static FrontAssignment clear() {
            return new FrontAssignment(FrontGroupWrite.CLEAR, null, List.of());
        }

        static FrontAssignment replace(Long primaryNodeId, List<Long> nodeIds) {
            return new FrontAssignment(FrontGroupWrite.REPLACE, primaryNodeId, nodeIds);
        }
    }

    /**
     * 判定这次保存对前置节点组的意图。三条路互斥，判定顺序就是优先级：
     * <ol>
     *   <li><b>显式要求重新分配</b>（{@code reallocateFront}）→ 调分配器按故障域算一组，
     *       忽略 {@code frontNodeId}；算不出候选时结果是空组，与「清空」同样落库。</li>
     *   <li><b>声明的主节点与库里现值不同</b> → 按 {@code frontNodeId} 的字面语义应用：
     *       null 即清空整组，具体 id 即手工指定这一个节点（运维逃生口）。</li>
     *   <li><b>与现值相同</b> → 这次保存没有动第一跳，user_front_node 原样不碰。</li>
     * </ol>
     * 第 3 条是这个整体保存接口的必要条款，不是可省的优化：管理端改备注、停用/恢复/吊销、
     * 只改落地节点，全都经 userToForm 把现值原样带回来。二期上线时这些保存被当作
     * 「管理员显式指定了单个节点」，每一次都把按故障域分散好的一组砍成一个，而且静默
     * ——冗余在生产里根本不会发生。要把已有多节点组强行收敛成当前这个主节点（而不是换一个），
     * 先选「不分配」保存、再指定它；这个方向极少用，不值得为它在接口上再加一种取值。
     */
    private FrontAssignment resolveFrontAssignment(Long id, UserDto user, UserSaveRequest request) {
        if (request.isReallocateFront()) {
            FrontNodeAllocator.AllocationResult allocation = frontNodeAllocator.allocate(id);
            return FrontAssignment.replace(allocation.primaryNodeId(), allocation.nodeIds());
        }
        if (Objects.equals(request.getFrontNodeId(), user.getFrontNodeId())) {
            return FrontAssignment.keep(user.getFrontNodeId());
        }
        if (request.getFrontNodeId() == null) {
            return FrontAssignment.clear();
        }
        validateNode(request.getFrontNodeId(), NodeRole.FRONT);
        return FrontAssignment.replace(request.getFrontNodeId(), List.of(request.getFrontNodeId()));
    }

    @Override
    public void delete(Long id) {
        UserDto user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        // 管理员账号受保护，不允许删除（理由见 update 里的说明）
        if (user.getRole() == UserRole.ADMIN) {
            throw new BizException(BizCodeEnum.ADMIN_USER_PROTECTED);
        }
        // user_front_node 对 app_user 的外键带 ON DELETE CASCADE，关联行由数据库自动清掉，
        // 与 subscription/user_device 等表一致，不需要应用层重复处理
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

    /**
     * frontNodeIds 由调用方传入（page() 批量取、get() 单个取），本方法不再自己查库——
     * 与上面 activeSubscriptions 的组装方式保持一致，两条批量路径不能一条批量一条逐行。
     */
    private AdminUserResponse toResponse(UserDto user, Map<Long, ProxyNodeDto> nodes,
                                         List<AdminUserResponse.ActiveSubscriptionBrief> activeSubscriptions,
                                         List<Long> frontNodeIds) {
        ProxyNodeDto front = nodes.get(user.getFrontNodeId());
        ProxyNodeDto land = user.getLandNodeId() == null ? null : nodes.get(user.getLandNodeId());

        // 完整前置组（不止 front_node_id 那个「主」节点）：管理端按故障域分组展示，
        // 并据 failureDomainCount 判断是否「入口无冗余」（详见 AdminUserResponse 字段注释）
        List<AdminUserResponse.FrontNodeBrief> frontNodes = frontNodeIds.stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .map(node -> new AdminUserResponse.FrontNodeBrief(
                        node.getId(), node.getName(), node.getFailureDomain()))
                .toList();
        int failureDomainCount = (int) frontNodes.stream()
                .map(AdminUserResponse.FrontNodeBrief::failureDomain)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return new AdminUserResponse(
                user.getId(),
                user.getSubject(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getFrontNodeId(),
                front == null ? null : front.getName(),
                frontNodes,
                failureDomainCount,
                user.getLandNodeId(),
                land == null ? null : land.getName(),
                land == null ? null : land.getEgressIp(),
                activeSubscriptions,
                user.getRemark(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
