package ai.mintpop.lane.service;

import ai.mintpop.lane.response.LinkConfigResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;

/**
 * 从渲染好的链路配置算出 configVersion：只有「客户端会跑的部分」进哈希。
 * <p>
 * 判定目标只有一个——用户需不需要热更新——所以哈希对象是最终下发的节点 map（订阅参数已叠保活覆盖），
 * 而不是库里的原始参数；不必维护「哪些字段影响连接」的白名单。组内节点按规范化字符串排序去重成集合
 * （库里顺序变了不算变化），组间保顺位（主用备用换位算变化）。排除 failureDomain（只是上报关联键）、
 * 席位凭据、ttl 与 configVersion 自身。是纯函数，与数据库无关。
 */
public final class LinkConfigVersion {

    /** 键排序 + map 条目按键排序：同一份参数不论 YAML 里怎么排列都得到同一串 */
    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private LinkConfigVersion() {
    }

    public static String of(LinkConfigResponse config) {
        return sha256Hex(canonical(config));
    }

    /** 包级可见供测试观察规范化形态 */
    static String canonical(LinkConfigResponse config) {
        StringBuilder sb = new StringBuilder();
        for (LinkConfigResponse.FrontGroup group : config.frontGroups()) {
            TreeSet<String> nodes = new TreeSet<>();
            for (Map<String, Object> node : group.nodes()) {
                nodes.add(json(node));
            }
            sb.append("group[").append(String.join(",", nodes)).append("]\n");
        }
        sb.append("land=").append(json(config.land())).append('\n');
        sb.append("egress=").append(config.expectedEgressIp() == null ? "" : config.expectedEgressIp()).append('\n');
        sb.append("tz=").append(config.egressTimezone() == null ? "" : config.egressTimezone()).append('\n');
        return sb.toString();
    }

    private static String json(Object value) {
        try {
            return CANONICAL.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("配置序列化失败", e);
        }
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
