package ai.mintpop.lane.service;

import ai.mintpop.lane.client.SubFetchClient;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.parser.SubNode;
import ai.mintpop.lane.parser.SubYamlParser;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.NodeGroupCreateRequest;
import ai.mintpop.lane.request.NodeGroupImportRequest;
import ai.mintpop.lane.request.NodeGroupRenameRequest;
import ai.mintpop.lane.response.NodeGroupResponse;
import ai.mintpop.lane.response.SubPreviewNodeResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class AdminNodeGroupServiceImpl implements AdminNodeGroupService {

    /** proxy_node.name 列的长度上限，撞名加后缀时的截断依据 */
    private static final int NODE_NAME_MAX_CODE_POINTS = 64;

    private final NodeGroupRepository groupRepository;
    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final SubFetchClient subFetchClient;
    private final SubYamlParser subYamlParser;
    private final TransactionTemplate transactionTemplate;

    public AdminNodeGroupServiceImpl(NodeGroupRepository groupRepository, ProxyNodeRepository nodeRepository,
                                     UserRepository userRepository, SubFetchClient subFetchClient,
                                     SubYamlParser subYamlParser, TransactionTemplate transactionTemplate) {
        this.groupRepository = groupRepository;
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.subFetchClient = subFetchClient;
        this.subYamlParser = subYamlParser;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public List<SubPreviewNodeResponse> preview(String subUrl) {
        return fetchAndParse(subUrl).stream()
                .map(node -> toPreview(node, false))
                .toList();
    }

    @Override
    public Long create(NodeGroupCreateRequest request) {
        if (groupRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_GROUP_NAME_DUPLICATED);
        }
        // 先拉订阅再建分组：拉取失败时不留下空分组；
        // 拉取解析是外呼 HTTP（最坏耗时可达约 25s），不能放进事务里独占数据库连接，
        // 故只把「建分组 + 导入」这段真正落库的操作交给 transactionTemplate 包一个事务
        List<SubNode> nodes = fetchAndParse(request.getSubUrl());

        NodeGroupDto group = new NodeGroupDto();
        group.setName(request.getName());
        group.setSubUrl(request.getSubUrl());
        group.setRemark(request.getRemark());

        return transactionTemplate.execute(status -> {
            Long groupId = wrapUniqueViolation(() -> groupRepository.create(group));
            importNodes(groupId, nodes, request.getSelectedNames());
            return groupId;
        });
    }

    @Override
    public List<NodeGroupResponse> list() {
        return groupRepository.findAll().stream()
                .map(group -> new NodeGroupResponse(
                        group.getId(),
                        group.getName(),
                        maskUrl(group.getSubUrl()),
                        nodeRepository.countByGroupId(group.getId()),
                        group.getRemark(),
                        group.getCreatedAt(),
                        group.getUpdatedAt()))
                .toList();
    }

    @Override
    public void rename(Long id, NodeGroupRenameRequest request) {
        NodeGroupDto group = getGroup(id);
        if (!group.getName().equals(request.getName()) && groupRepository.existsByName(request.getName())) {
            throw new BizException(BizCodeEnum.NODE_GROUP_NAME_DUPLICATED);
        }
        group.setName(request.getName());
        group.setRemark(request.getRemark());
        wrapUniqueViolation(() -> {
            groupRepository.update(group);
            return null;
        });
    }

    @Override
    public List<SubPreviewNodeResponse> refreshPreview(Long id) {
        NodeGroupDto group = getGroup(id);
        return fetchAndParse(group.getSubUrl()).stream()
                .map(node -> toPreview(node,
                        nodeRepository.findByGroupIdAndSourceName(id, node.sourceName()).isPresent()))
                .toList();
    }

    @Override
    public void importNodes(Long id, NodeGroupImportRequest request) {
        // 取分组、拉取解析都是只读操作，同样挪到事务外，避免外呼期间占用数据库连接；
        // 只有真正落库的「导入」交给 transactionTemplate 包事务
        NodeGroupDto group = getGroup(id);
        List<SubNode> nodes = fetchAndParse(group.getSubUrl());
        transactionTemplate.executeWithoutResult(status -> importNodes(id, nodes, request.getSelectedNames()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getGroup(id);
        List<ProxyNodeDto> nodes = nodeRepository.findByGroupId(id);
        // 先整体校验再删：不做「删到一半发现被引用」的部分删除
        for (ProxyNodeDto node : nodes) {
            if (userRepository.existsByFrontNodeId(node.getId())
                    || userRepository.countByLandNodeId(node.getId()) > 0) {
                throw new BizException(BizCodeEnum.NODE_GROUP_IN_USE);
            }
        }
        nodes.forEach(node -> nodeRepository.deleteById(node.getId()));
        groupRepository.deleteById(id);
    }

    /**
     * 唯一约束的兜底：上面的预检查（existsByName）给的是可读错误，但两个管理员同时提交仍可能撞车，
     * 那时靠数据库的唯一索引挡住。与 {@code AdminNodeServiceImpl.wrapUniqueViolation} 同一模式。
     */
    private <T> T wrapUniqueViolation(Supplier<T> action) {
        try {
            return action.get();
        } catch (DuplicateKeyException e) {
            throw new BizException(BizCodeEnum.NODE_GROUP_NAME_DUPLICATED);
        }
    }

    private NodeGroupDto getGroup(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_GROUP_NOT_FOUND));
    }

    private List<SubNode> fetchAndParse(String subUrl) {
        return subYamlParser.parse(subFetchClient.fetch(subUrl));
    }

    private SubPreviewNodeResponse toPreview(SubNode node, boolean existed) {
        return new SubPreviewNodeResponse(node.sourceName(), node.sourceType(),
                node.serverAddr(), node.port(), node.suspectedInfo(), existed);
    }

    /**
     * 按勾选把订阅节点写进分组：同组内 sourceName 已存在的原地更新参数
     * （名称/状态/备注是管理员的手工痕迹，不动），不存在的新建入库。
     */
    private void importNodes(Long groupId, List<SubNode> nodes, List<String> selectedNames) {
        Map<String, SubNode> nodesByName = new LinkedHashMap<>();
        nodes.forEach(node -> nodesByName.putIfAbsent(node.sourceName(), node));

        for (String selected : selectedNames) {
            SubNode sub = nodesByName.get(selected);
            if (sub == null) {
                throw new BizException(BizCodeEnum.SELECTED_NODE_MISSING);
            }
            Optional<ProxyNodeDto> existing = nodeRepository.findByGroupIdAndSourceName(groupId, selected);
            if (existing.isPresent()) {
                ProxyNodeDto node = existing.get();
                node.setServerAddr(sub.serverAddr());
                node.setPort(sub.port());
                node.setSourceType(sub.sourceType());
                node.setSecret(sub.params());
                nodeRepository.update(node);
            } else {
                ProxyNodeDto node = new ProxyNodeDto();
                node.setName(uniqueNodeName(selected));
                node.setRole(NodeRole.FRONT);
                node.setProtocol(NodeProtocol.MIHOMO);
                node.setServerAddr(sub.serverAddr());
                node.setPort(sub.port());
                node.setExtraConfig(Map.of());
                node.setSecret(sub.params());
                node.setGroupId(groupId);
                node.setSourceName(selected);
                node.setSourceType(sub.sourceType());
                nodeRepository.create(node);
            }
        }
    }

    /** 撞全局唯一名时加「 (2)」「 (3)」后缀；按码点截断，不把 emoji 劈成半个代理对 */
    private String uniqueNodeName(String sourceName) {
        String base = truncateByCodePoints(sourceName, NODE_NAME_MAX_CODE_POINTS);
        if (!nodeRepository.existsByName(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            String suffix = " (" + i + ")";
            String candidate = truncateByCodePoints(base, NODE_NAME_MAX_CODE_POINTS - suffix.length()) + suffix;
            if (!nodeRepository.existsByName(candidate)) {
                return candidate;
            }
        }
    }

    private String truncateByCodePoints(String s, int maxCodePoints) {
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, maxCodePoints));
    }

    /** 回显用的打码链接：只留 scheme 与 host，token 一律不回传 */
    private String maskUrl(String subUrl) {
        try {
            URI uri = URI.create(subUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/…";
        } catch (Exception e) {
            return "（无法解析的链接）";
        }
    }
}
