package ai.mintpop.lane.service;

import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.dto.UserDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.NodeSaveRequest;
import ai.mintpop.lane.response.AdminNodeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AdminNodeServiceImpl implements AdminNodeService {

    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final NodeGroupRepository groupRepository;

    public AdminNodeServiceImpl(ProxyNodeRepository nodeRepository, UserRepository userRepository,
                                 NodeGroupRepository groupRepository) {
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public List<AdminNodeResponse> list(NodeRole role) {
        Map<Long, String> groupNames = groupRepository.findAll().stream()
                .collect(Collectors.toMap(NodeGroupDto::getId, NodeGroupDto::getName));
        return nodeRepository.findAll(role).stream().map(node -> toResponse(node, groupNames)).toList();
    }

    @Override
    public Long create(NodeSaveRequest request) {
        // MIHOMO 是订阅导入专用形态：整份参数加密、不能手填。手工新建一律拒绝
        if (request.getProtocol() == NodeProtocol.MIHOMO) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }

        if (nodeRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_NAME_DUPLICATED);
        }
        validateSecretKeysNotInExtraConfig(request);

        ProxyNodeDto node = new ProxyNodeDto();
        apply(node, request);
        node.setSecret(request.getSecret());
        return wrapUniqueViolation(() -> nodeRepository.create(node));
    }

    @Override
    public void update(Long id, NodeSaveRequest request) {
        ProxyNodeDto node = nodeRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));

        if (!node.getName().equals(request.getName()) && nodeRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_NAME_DUPLICATED);
        }

        // 订阅导入的节点参数由「重新拉取」统一更新，编辑接口只放行名称/状态/备注；
        // 协议也不许改——它的参数形态（整份加密）与其它协议（分键加密）互不兼容
        if (node.getProtocol() == NodeProtocol.MIHOMO) {
            if (request.getProtocol() != NodeProtocol.MIHOMO) {
                throw new BizException(BizCodeEnum.PARAM_INVALID);
            }
            node.setName(request.getName());
            node.setStatus(request.getStatus());
            node.setRemark(request.getRemark());
            wrapUniqueViolation(() -> {
                nodeRepository.update(node);
                return null;
            });
            return;
        }
        // 反向同理：其它协议的节点也不许改成 MIHOMO
        if (request.getProtocol() == NodeProtocol.MIHOMO) {
            throw new BizException(BizCodeEnum.PARAM_INVALID);
        }

        validateSecretKeysNotInExtraConfig(request);

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
        wrapUniqueViolation(() -> {
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
     * 且会被列表接口原样回显。{@link ai.mintpop.lane.enumeration.NodeProtocol#secretKeys()}
     * 定义了每种协议下哪些键属于敏感键，这里是它唯一的调用点——没有这道校验，
     * 调用方随手把密码塞进 extraConfig，密码就会明文存库并被 GET 接口吐回去。
     */
    private void validateSecretKeysNotInExtraConfig(NodeSaveRequest request) {
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
    private <T> T wrapUniqueViolation(Supplier<T> action) {
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
        // 出口 IP 是落地节点的属性：非 LAND 一律清空（含角色由 LAND 改走时清掉残值），空白归一成 null
        String egressIp = request.getEgressIp() == null ? null : request.getEgressIp().trim();
        node.setEgressIp(request.getRole() == NodeRole.LAND && egressIp != null && !egressIp.isEmpty()
                ? egressIp : null);
        node.setStatus(request.getStatus());
        node.setRemark(request.getRemark());
    }

    private AdminNodeResponse toResponse(ProxyNodeDto node, Map<Long, String> groupNames) {
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
                node.getEgressIp(),
                node.getStatus(),
                node.getRemark(),
                node.getSecret() != null && !node.getSecret().isEmpty(),
                assignedUserName,
                node.getGroupId(),
                node.getGroupId() == null ? null : groupNames.get(node.getGroupId()),
                node.getSourceType(),
                node.getCreatedAt(),
                node.getUpdatedAt());
    }
}
