package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 前置节点的下发参数覆盖（lane.front-tuning.*），按 mihomo type 分派。
 *
 * 治的是「anytls 默认空闲 30 秒就断连接池、且不保留常驻连接」——
 * Claude Code 是突发式流量，思考间隔常超 30 秒，于是几乎每次请求都要重新握手，
 * 而握手正是最容易被干扰的时刻。min-idle-session 让内核在后台补连接，
 * 把握手从用户请求的关键路径上挪走。
 *
 * 表里没有的协议原样透传；整段留空即完全恢复现状（这是回退手段）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lane.front-tuning")
public class FrontTuningProperties {
    /** key 是 mihomo type（如 anytls），value 是要合并进下发配置的键值对 */
    private Map<String, Map<String, Object>> protocols = Map.of();
}
