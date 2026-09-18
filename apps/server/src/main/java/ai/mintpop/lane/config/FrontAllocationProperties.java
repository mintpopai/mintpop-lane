package ai.mintpop.lane.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 前置节点分配配置（lane.front.*）。
 * nodes-per-domain 是「每个故障域取几个」：取多了客户端健康检查开销大，
 * 取少了落地层冗余不够。默认 3，可按三期的上报数据再调。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lane.front")
public class FrontAllocationProperties {
    private int nodesPerDomain = 3;
}
