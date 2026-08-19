package ai.mintpop.pier.service;

import ai.mintpop.pier.dto.PageResult;
import ai.mintpop.pier.dto.ProxyNodeDto;
import ai.mintpop.pier.dto.UserDto;
import ai.mintpop.pier.enumeration.BizCodeEnum;
import ai.mintpop.pier.enumeration.NodeRole;
import ai.mintpop.pier.enumeration.UserRole;
import ai.mintpop.pier.exception.BizException;
import ai.mintpop.pier.repository.ProxyNodeRepository;
import ai.mintpop.pier.repository.UserRepository;
import ai.mintpop.pier.request.UserSaveRequest;
import ai.mintpop.pier.response.AdminUserResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final ProxyNodeRepository nodeRepository;

    public AdminUserServiceImpl(UserRepository userRepository, ProxyNodeRepository nodeRepository) {
        this.userRepository = userRepository;
        this.nodeRepository = nodeRepository;
    }

    @Override
    public PageResult<AdminUserResponse> page(String keyword, long pageNo, long pageSize) {
        PageResult<UserDto> page = userRepository.search(keyword, pageNo, pageSize);
        // 一次取出全部节点做成字典，避免每行用户都去查一次节点
        Map<Long, ProxyNodeDto> nodes = nodeRepository.findAll(null).stream()
                .collect(Collectors.toMap(ProxyNodeDto::getId, Function.identity()));

        List<AdminUserResponse> records = page.records().stream()
                .map(user -> toResponse(user, nodes))
                .toList();

        return new PageResult<>(records, page.total(), page.pageNo(), page.pageSize());
    }

    @Override
    public Long create(UserSaveRequest request) {
        if (userRepository.existsBySubject(request.getSubject())) {
            throw new BizException(BizCodeEnum.USER_ALREADY_EXISTS);
        }
        校验节点(request.getFrontNodeId(), NodeRole.FRONT);
        校验落地可用(request.getLandNodeId(), null);

        UserDto user = new UserDto();
        user.setSubject(request.getSubject());
        user.setName(request.getName());
        // 角色固定 MEMBER：提权只能改库
        user.setRole(UserRole.MEMBER);
        user.setStatus(request.getStatus());
        user.setFrontNodeId(request.getFrontNodeId());
        user.setLandNodeId(request.getLandNodeId());
        user.setClaudeCredential(空白转null(request.getClaudeCredential()));

        return 兜住唯一约束(() -> userRepository.create(user));
    }

    @Override
    public void update(Long id, UserSaveRequest request) {
        UserDto user = userRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.USER_NOT_FOUND));

        if (!user.getSubject().equals(request.getSubject())
                && userRepository.existsBySubject(request.getSubject())) {
            throw new BizException(BizCodeEnum.USER_ALREADY_EXISTS);
        }
        校验节点(request.getFrontNodeId(), NodeRole.FRONT);
        校验落地可用(request.getLandNodeId(), id);

        user.setSubject(request.getSubject());
        user.setName(request.getName());
        user.setStatus(request.getStatus());
        user.setFrontNodeId(request.getFrontNodeId());
        user.setLandNodeId(request.getLandNodeId());
        // 凭据留空表示沿用原值：页面上看不到原凭据，不能因为没重填就把它清掉
        String credential = 空白转null(request.getClaudeCredential());
        if (credential != null) {
            user.setClaudeCredential(credential);
        }
        // role 不从入参取，沿用库里的值

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
     */
    private <T> T 兜住唯一约束(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("uk_app_user_land_node")) {
                throw new BizException(BizCodeEnum.LAND_NODE_OCCUPIED);
            }
            throw new BizException(BizCodeEnum.USER_ALREADY_EXISTS);
        }
    }

    private static String 空白转null(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private AdminUserResponse toResponse(UserDto user, Map<Long, ProxyNodeDto> nodes) {
        ProxyNodeDto front = nodes.get(user.getFrontNodeId());
        ProxyNodeDto land = user.getLandNodeId() == null ? null : nodes.get(user.getLandNodeId());

        return new AdminUserResponse(
                user.getId(),
                user.getSubject(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                user.getFrontNodeId(),
                front == null ? null : front.getName(),
                user.getLandNodeId(),
                land == null ? null : land.getName(),
                land == null ? List.of() : land.getEgressIps(),
                user.getClaudeCredential() != null && !user.getClaudeCredential().isBlank(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
