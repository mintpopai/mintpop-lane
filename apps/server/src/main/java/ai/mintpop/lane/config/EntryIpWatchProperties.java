package ai.mintpop.lane.config;

import ai.mintpop.lane.enumeration.DnsVantage;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 入口 IP 观测配置（entry-ip-watch.*）。
 * 中转入口常按运营商分线路返回不同 IP，vantages 给出四个运营商视角各自的代表性子网
 * （EDNS Client Subnet），供 {@code EcsDnsClient} 逐个视角解析。默认值可在配置文件按需覆盖。
 * <p>
 * 本任务（订阅尽调）只用到 vantages；巡检间隔 interval 由 Task 11 补充。
 */
@Data
@Component
@ConfigurationProperties(prefix = "entry-ip-watch")
public class EntryIpWatchProperties {

    private Map<DnsVantage, String> vantages = defaultVantages();

    private static Map<DnsVantage, String> defaultVantages() {
        Map<DnsVantage, String> defaults = new LinkedHashMap<>();
        defaults.put(DnsVantage.CHINA_TELECOM, "202.96.128.0/24");
        defaults.put(DnsVantage.CHINA_UNICOM, "202.106.195.0/24");
        defaults.put(DnsVantage.CHINA_MOBILE, "120.196.165.0/24");
        defaults.put(DnsVantage.OVERSEAS, "8.8.8.0/24");
        return defaults;
    }
}
