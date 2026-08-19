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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    public AdminUserServiceImpl(UserRepository userRepository, ProxyNodeRepository nodeRepository,
                                 SubscriptionRepository subscriptionRepository) {
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public PageResult<AdminUserResponse> page(String keyword, Boolean hasActiveSubscription,
                                              long pageNo, long pageSize) {
        PageResult<UserDto> page = userRepository.search(keyword, hasActiveSubscription, pageNo, pageSize);
        Map<Long, ProxyNodeDto> nodes = nodeRepository.findAll(null).stream()
                .collect(Collectors.toMap(ProxyNodeDto::getId, Function.identity()));

        // 一次取回本页所有用户的订阅，避免逐行查询
        List<Long> userIds = page.records().stream().map(UserDto::getId).toList();
        LocalDateTime now = LocalDateTime.now();
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

    @Override
    public void update(Long id, UserSaveRequest request) {
        UserDto user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));

        if (request.getFrontNodeId() != null) {
            校验节点(request.getFrontNodeId(), NodeRole.FRONT);
        }
        校验落地可用(request.getLandNodeId(), id);

        user.setName(request.getName());
        user.setStatus(request.getStatus());
        user.setFrontNodeId(request.getFrontNodeId());
        user.setLandNodeId(request.getLandNodeId());
        // subject/email/role 不从入参取，沿用库里的值

        兜住唯一约束(() -> {
            userRepository.update(user);
            return null;
        });
    }

    @Override
    public void delete(Long id) {
        userRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));
        userRepository.deleteById(id);
    }

    private void 校验节点(Long nodeId, NodeRole expectedRole) {
        ProxyNodeDto node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));
        if (node.getRole() != expectedRole) {
            throw new BizException(BizCodeEnum.NODE_ROLE_MISMATCH);
        }
    }

    /** 落地节点必须存在、角色正确，且没有被别人占用（selfId 为当前用户，允许保留自己的） */
    private void 校验落地可用(Long landNodeId, Long selfId) {
        if (landNodeId == null) {
            return;
        }
        校验节点(landNodeId, NodeRole.LAND);

        userRepository.findByLandNodeId(landNodeId)
                .filter(occupied -> !Objects.equals(occupied.getId(), selfId))
                .ifPresent(occupied -> {
                    throw new BizException(BizCodeEnum.LAND_NODE_OCCUPIED);
                });
    }

    /**
     * 唯一约束的兜底：上面的预检查给的是可读错误，但两个管理员同时提交仍可能撞车，
     * 那时靠数据库的唯一索引挡住，这里把它翻译成对应的业务错误码。
     * 用户表现存的唯一索引只剩 uk_app_user_land_node（subject 不再从接口改，不会冲突到这里）。
     */
    private <T> T 兜住唯一约束(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(BizCodeEnum.LAND_NODE_OCCUPIED);
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
                user.getName(),
                user.getRole(),
                user.getStatus(),
                user.getFrontNodeId(),
                front == null ? null : front.getName(),
                user.getLandNodeId(),
                land == null ? null : land.getName(),
                land == null ? List.of() : land.getEgressIps(),
                activeSubscriptions,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
