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

    /**
     * 默认值经实测：用这四个子网查 EDNS Client Subnet 时，同一域名电信解析到 GCP 东京、
     * 联通/移动/海外解析到 AWS 香港，同一子网重复查 6 次结果稳定——正是靠它们才发现
     * 「中转入口按运营商分线路」这件事。
     */
    private static Map<DnsVantage, String> defaultVantages() {
        Map<DnsVantage, String> defaults = new LinkedHashMap<>();
        defaults.put(DnsVantage.CHINA_TELECOM, "202.96.209.0/24");
        defaults.put(DnsVantage.CHINA_UNICOM, "123.125.114.0/24");
        defaults.put(DnsVantage.CHINA_MOBILE, "120.196.212.0/24");
        defaults.put(DnsVantage.OVERSEAS, "104.16.0.0/24");
        return defaults;
    }
}
