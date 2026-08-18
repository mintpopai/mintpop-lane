package com.mintpop.server.service;

import com.mintpop.server.dto.ProxyNodeDto;
import com.mintpop.server.dto.UserDto;
import com.mintpop.server.enumeration.BizCodeEnum;
import com.mintpop.server.enumeration.NodeRole;
import com.mintpop.server.exception.BizException;
import com.mintpop.server.repository.ProxyNodeRepository;
import com.mintpop.server.repository.UserRepository;
import com.mintpop.server.request.NodeSaveRequest;
import com.mintpop.server.response.AdminNodeResponse;
import org.springframework.stereotype.Service;

import java.util.List;

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
        ProxyNodeDto node = new ProxyNodeDto();
        apply(node, request);
        node.setSecret(request.getSecret());
        return nodeRepository.create(node);
    }

    @Override
    public void update(Long id, NodeSaveRequest request) {
        ProxyNodeDto node = nodeRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));

        if (!node.getName().equals(request.getName()) && nodeRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_NAME_DUPLICATED);
        }

        apply(node, request);
        // 敏感键留空表示沿用原值：管理端页面上本就看不到原密码，不能因为没重填就被清掉
        if (request.getSecret() != null && !request.getSecret().isEmpty()) {
            node.setSecret(request.getSecret());
        }
        nodeRepository.update(node);
    }

    @Override
    public void delete(Long id) {
        nodeRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));

        if (userRepository.existsByFrontNodeId(id) || userRepository.findByLandNodeId(id).isPresent()) {
            throw new BizException(BizCodeEnum.NODE_IN_USE);
        }
        nodeRepository.deleteById(id);
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
