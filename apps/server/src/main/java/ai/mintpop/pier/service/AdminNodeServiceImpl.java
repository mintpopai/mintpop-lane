package ai.mintpop.pier.service;

import ai.mintpop.pier.dto.ProxyNodeDto;
import ai.mintpop.pier.dto.UserDto;
import ai.mintpop.pier.enumeration.BizCodeEnum;
import ai.mintpop.pier.enumeration.NodeRole;
import ai.mintpop.pier.exception.BizException;
import ai.mintpop.pier.repository.ProxyNodeRepository;
import ai.mintpop.pier.repository.UserRepository;
import ai.mintpop.pier.request.NodeSaveRequest;
import ai.mintpop.pier.response.AdminNodeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class AdminNodeServiceImpl implements AdminNodeService {

    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;

    public AdminNodeServiceImpl(ProxyNodeRepository nodeRepository, UserRepository userRepository) {
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<AdminNodeResponse> list(NodeRole role) {
        return nodeRepository.findAll(role).stream().map(this::toResponse).toList();
    }

    @Override
    public Long create(NodeSaveRequest request) {
        if (nodeRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_NAME_DUPLICATED);
        }
        校验敏感键不与extraConfig重叠(request);

        ProxyNodeDto node = new ProxyNodeDto();
        apply(node, request);
        node.setSecret(request.getSecret());
        return 兜住唯一约束(() -> nodeRepository.create(node));
    }

    @Override
    public void update(Long id, NodeSaveRequest request) {
        ProxyNodeDto node = nodeRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));

        if (!node.getName().equals(request.getName()) && nodeRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_NAME_DUPLICATED);
        }
        校验敏感键不与extraConfig重叠(request);

        // 角色变更前先查它是否正被用户引用：已被当前端/落地出口使用的节点悄悄改角色，
        // 会让分配它的用户在无人复查的情况下跑到一个用途不符的节点上
        if (request.getRole() != node.getRole()
                && (userRepository.existsByFrontNodeId(id) || userRepository.findByLandNodeId(id).isPresent())) {
            throw new BizException(BizCodeEnum.NODE_IN_USE);
        }

        apply(node, request);
        // 敏感键留空表示沿用原值：管理端页面上本就看不到原密码，不能因为没重填就被清掉
        if (request.getSecret() != null && !request.getSecret().isEmpty()) {
            node.setSecret(request.getSecret());
        }
        兜住唯一约束(() -> {
            nodeRepository.update(node);
            return null;
        });
    }

    @Override
    public void delete(Long id) {
        nodeRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));

        if (userRepository.existsByFrontNodeId(id) || userRepository.findByLandNodeId(id).isPresent()) {
            throw new BizException(BizCodeEnum.NODE_IN_USE);
        }
        nodeRepository.deleteById(id);
    }

    /**
     * 敏感键只能走 {@code secret} 字段，不能混进 {@code extraConfig}：后者明文落库，
     * 且会被列表接口原样回显。{@link ai.mintpop.pier.enumeration.NodeProtocol#secretKeys()}
     * 定义了每种协议下哪些键属于敏感键，这里是它唯一的调用点——没有这道校验，
     * 调用方随手把密码塞进 extraConfig，密码就会明文存库并被 GET 接口吐回去。
     */
    private void 校验敏感键不与extraConfig重叠(NodeSaveRequest request) {
        Map<String, Object> extraConfig = request.getExtraConfig();
        if (extraConfig == null || extraConfig.isEmpty()) {
            return;
        }
        if (!Collections.disjoint(extraConfig.keySet(), request.getProtocol().secretKeys())) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }
    }

    /**
     * 唯一约束的兜底：上面的预检查给的是可读错误，但两个管理员同时提交仍可能撞车，
     * 那时靠数据库的唯一索引挡住。节点表只有一个唯一索引（name），
     * 因此不像 {@code AdminUserServiceImpl} 那样需要按消息内容分支。
     */
    private <T> T 兜住唯一约束(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(BizCodeEnum.NODE_NAME_DUPLICATED);
        }
    }

    private void apply(ProxyNodeDto node, NodeSaveRequest request) {
        node.setName(request.getName());
        node.setRole(request.getRole());
        node.setProtocol(request.getProtocol());
        node.setServerAddr(request.getServerAddr());
        node.setPort(request.getPort());
        node.setExtraConfig(request.getExtraConfig());
        node.setEgressIps(request.getEgressIps());
        node.setStatus(request.getStatus());
        node.setRemark(request.getRemark());
    }

    private AdminNodeResponse toResponse(ProxyNodeDto node) {
        String assignedUserName = node.getRole() == NodeRole.LAND
                ? userRepository.findByLandNodeId(node.getId()).map(UserDto::getName).orElse(null)
                : null;

        return new AdminNodeResponse(
                node.getId(),
                node.getName(),
                node.getRole(),
                node.getProtocol(),
                node.getServerAddr(),
                node.getPort(),
                node.getExtraConfig(),
                node.getEgressIps(),
                node.getStatus(),
                node.getRemark(),
                node.getSecret() != null && !node.getSecret().isEmpty(),
                assignedUserName,
                node.getCreatedAt(),
                node.getUpdatedAt());
    }
}
