package ai.mintpop.lane.parser;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅 YAML（Clash/mihomo 格式）解析器。只认顶层 proxies 列表，
 * proxy-groups / rules / dns 等其余段一概忽略。
 */
@Component
public class SubYamlParser {

    /** 名称含这些关键词的多半是机场塞的「信息条目」（剩余流量/到期时间），前端默认不勾选但仍展示 */
    private static final List<String> 信息条目关键词 =
            List.of("流量", "重置", "到期", "过期", "剩余", "套餐", "官网");

    public List<SubNode> parse(String yamlText) {
        Object root;
        try {
            // SafeConstructor：订阅内容来自外部，绝不允许 YAML 反序列化出任意对象
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
        } catch (Exception e) {
            throw new BizException(BizCodeEnum.SUB_PARSE_FAILED);
        }
        if (!(root instanceof Map<?, ?> rootMap) || !(rootMap.get("proxies") instanceof List<?> proxies)) {
            throw new BizException(BizCodeEnum.SUB_PARSE_FAILED);
        }
        List<SubNode> nodes = new ArrayList<>();
        for (Object item : proxies) {
            if (item instanceof Map<?, ?> raw) {
                SubNode node = 转节点(raw);
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        if (nodes.isEmpty()) {
            throw new BizException(BizCodeEnum.SUB_PARSE_FAILED);
        }
        return nodes;
    }

    /** 缺 name/type/server/port 任一关键字段的条目返回 null（跳过），不让一条坏数据毁掉整次导入 */
    private SubNode 转节点(Map<?, ?> raw) {
        if (!(raw.get("name") instanceof String name) || name.isBlank()
                || !(raw.get("type") instanceof String type)
                || !(raw.get("server") instanceof String server)
                || !(raw.get("port") instanceof Number port)) {
            return null;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k instanceof String key && !"name".equals(key)) {
                params.put(key, v);
            }
        });
        boolean suspectedInfo = 信息条目关键词.stream().anyMatch(name::contains);
        return new SubNode(name, type, server, port.intValue(), params, suspectedInfo);
    }
}
