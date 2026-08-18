package com.mintpop.server.config;

import com.mintpop.server.entity.User;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 链路配置来源。
 * 第一版以配置文件承载节点池与用户绑定表，管控后台（本期）再换成数据库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mintpop.link")
public class LinkProperties {

    /** 第一跳：全员共用的美国机房机场节点，原样透传的 mihomo 节点配置 */
    private Map<String, Object> front = Map.of();

    /** 用户绑定表 */
    private List<User> users = List.of();

    /** 下发给客户端的配置有效期（秒） */
    private long ttlSeconds = 1800;
}
