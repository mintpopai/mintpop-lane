package ai.mintpop.lane.service;

import ai.mintpop.lane.client.EgressIpVerifier;
import ai.mintpop.lane.dto.NodeGroupDto;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.enumeration.EgressIpChangeSource;
import ai.mintpop.lane.enumeration.NodeProtocol;
import ai.mintpop.lane.enumeration.NodeRole;
import ai.mintpop.lane.exception.BizException;
import ai.mintpop.lane.repository.NodeGroupRepository;
import ai.mintpop.lane.repository.ProxyNodeRepository;
import ai.mintpop.lane.repository.UserFrontNodeRepository;
import ai.mintpop.lane.repository.UserRepository;
import ai.mintpop.lane.request.NodeSaveRequest;
import ai.mintpop.lane.response.AdminNodeResponse;
import ai.mintpop.lane.response.NodeProbeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.ZoneId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminNodeServiceImpl implements AdminNodeService {

    private final ProxyNodeRepository nodeRepository;
    private final UserRepository userRepository;
    private final UserFrontNodeRepository userFrontNodeRepository;
    private final NodeGroupRepository groupRepository;
    private final EgressIpVerifier.EgressProbe egressProbe;
    private final NodeNotifyService nodeNotifyService;

    public AdminNodeServiceImpl(ProxyNodeRepository nodeRepository, UserRepository userRepository,
                                 UserFrontNodeRepository userFrontNodeRepository,
                                 NodeGroupRepository groupRepository, EgressIpVerifier.EgressProbe egressProbe,
                                 NodeNotifyService nodeNotifyService) {
        this.nodeRepository = nodeRepository;
        this.userRepository = userRepository;
        this.userFrontNodeRepository = userFrontNodeRepository;
        this.groupRepository = groupRepository;
        this.egressProbe = egressProbe;
        this.nodeNotifyService = nodeNotifyService;
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
        // 角色与协议必须匹配：落地节点要供服务端出站，协议集比前置节点窄
        if (!request.getRole().allows(request.getProtocol())) {
            throw new BizException(BizCodeEnum.NODE_PROTOCOL_NOT_ALLOWED);
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

        // 重名检查按 id 排除自身：表是 ai_ci 排序规则，只改大小写时 existsByName 会匹配到自己
        if (nodeRepository.existsByNameExcludingId(request.getName(), id)) {
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
        // 角色与协议必须匹配：落地节点要供服务端出站，协议集比前置节点窄
        if (!request.getRole().allows(request.getProtocol())) {
            throw new BizException(BizCodeEnum.NODE_PROTOCOL_NOT_ALLOWED);
        }

        validateSecretKeysNotInExtraConfig(request);

        // 角色变更前先查它是否正被用户引用：已被当前端/落地出口使用的节点悄悄改角色，
        // 会让分配它的用户在无人复查的情况下跑到一个用途不符的节点上
        if (request.getRole() != node.getRole() && isReferenced(id)) {
            throw new BizException(BizCodeEnum.NODE_IN_USE);
        }

        String previousEgressIp = node.getEgressIp();
        String previousEgressTimezone = node.getEgressTimezone();
        apply(node, request);
        // 敏感键留空表示沿用原值：管理端页面上本就看不到原密码，不能因为没重填就被清掉
        if (request.getSecret() != null && !request.getSecret().isEmpty()) {
            node.setSecret(request.getSecret());
        }
        wrapUniqueViolation(() -> {
            nodeRepository.update(node);
            return null;
        });
        // 出口 IP 改了（含检测后一键回填、首次登记、清空）才通知，且只在库已改完之后。
        // 时区按提交值写入：管理端表单在改 IP 时已按 GeoIP 联动预填，管理员也可以自己改，服务端不越权覆盖
        // try-catch 兜底任务「提交」阶段的异常（如停机中执行器已关闭）：@Async 只消化执行中的异常，
        // 提交失败会同步冒回本线程，不能让它把一次已成功的更新变成 500
        if (!Objects.equals(previousEgressIp, node.getEgressIp())) {
            try {
                nodeNotifyService.notifyEgressIpChanged(node, previousEgressIp, previousEgressTimezone,
                        EgressIpChangeSource.ADMIN);
            } catch (Exception e) {
                log.warn("出口 IP 变更通知任务提交失败（库已改完）nodeId={}", id, e);
            }
        }
    }

    @Override
    public void delete(Long id) {
        nodeRepository.findById(id).orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));

        if (isReferenced(id)) {
            throw new BizException(BizCodeEnum.NODE_IN_USE);
        }
        nodeRepository.deleteById(id);
    }

    @Override
    public NodeProbeResponse probe(Long id) {
        ProxyNodeDto node = nodeRepository.findById(id)
                .orElseThrow(() -> new BizException(BizCodeEnum.NODE_NOT_FOUND));
        // 前置节点跑 trojan/vmess 等加密协议，服务端不带 mihomo 内核连不上，探测只对落地节点有意义
        if (node.getRole() != NodeRole.LAND) {
            throw new BizException(BizCodeEnum.NODE_PROBE_UNSUPPORTED);
        }

        String registered = node.getEgressIp();
        long startedAt = System.nanoTime();
        String actual;
        try {
            actual = egressProbe.currentEgressIp(node);
        } catch (Exception e) {
            // 不通是检测要给出的答案，不是请求本身出错：正常返回并带上原因，页面据此展示
            long latencyMs = elapsedMillis(startedAt);
            return new NodeProbeResponse(false, latencyMs, null, registered, null, describe(e));
        }
        long latencyMs = elapsedMillis(startedAt);
        Boolean matched = registered == null ? null : registered.equals(actual);
        return new NodeProbeResponse(true, latencyMs, actual, registered, matched, null);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    /**
     * 该节点是否正被引用：三种引用形状都要查——某人的主前置节点、某人的落地节点、
     * 或某人前置集合里的非主成员（二期新增，只在 user_front_node 里，不体现在
     * app_user.front_node_id 上，漏查会在真正删除时撞上外键抛出原始数据库异常）。
     */
    private boolean isReferenced(Long nodeId) {
        return userRepository.existsByFrontNodeId(nodeId)
                || userRepository.countByLandNodeId(nodeId) > 0
                || userFrontNodeRepository.existsByNodeId(nodeId);
    }

    /** 取异常链上最内层的说明：Netty 的代理失败通常裹在 ResourceAccessException 里，外层信息不可读 */
    private static String describe(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
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
        String egressTimezone = request.getEgressTimezone() == null ? null : request.getEgressTimezone().trim();
        if (request.getRole() == NodeRole.LAND && egressTimezone != null && !egressTimezone.isEmpty()) {
            validateTimezone(egressTimezone);
            node.setEgressTimezone(egressTimezone);
        } else {
            node.setEgressTimezone(null);
        }
        // 容量只对 LAND 有意义；置 null 时列上无 ALWAYS 策略，新建走数据库默认值 10、更新保留原值。
        // 允许把容量改到低于当前已绑人数：只影响后续分配，不踢已绑定的用户
        node.setCapacity(request.getRole() == NodeRole.LAND ? request.getCapacity() : null);
        node.setStatus(request.getStatus());
        node.setRemark(request.getRemark());
    }

    /**
     * 时区存的是给后续业务直接消费的 IANA 名，坏值会把错误推迟到消费点才爆，
     * 这里在入口就挡掉。ZoneId.of 接受区域名（Asia/Tokyo）与偏移量写法，与前端校验同宽。
     */
    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new BizException(BizCodeEnum.NODE_TIMEZONE_INVALID);
        }
    }

    private AdminNodeResponse toResponse(ProxyNodeDto node, Map<Long, String> groupNames) {
        // 已绑人数实时统计、不落库，取消分配自然回补；容量是落地专属概念，非 LAND 两者都回 null
        boolean isLand = node.getRole() == NodeRole.LAND;
        Long assignedUserCount = isLand ? userRepository.countByLandNodeId(node.getId()) : null;

        return new AdminNodeResponse(
                node.getId(),
                node.getName(),
                node.getRole(),
                node.getProtocol(),
                node.getServerAddr(),
                node.getPort(),
                node.getExtraConfig(),
                node.getEgressIp(),
                node.getEgressTimezone(),
                node.getStatus(),
                node.getRemark(),
                node.getSecret() != null && !node.getSecret().isEmpty(),
                isLand ? node.getCapacity() : null,
                assignedUserCount,
                node.getGroupId(),
                node.getGroupId() == null ? null : groupNames.get(node.getGroupId()),
                node.getSourceType(),
                node.getFailureDomain(),
                node.getCreatedAt(),
                node.getUpdatedAt());
    }
}
