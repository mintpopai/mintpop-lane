package ai.mintpop.lane.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * IP → ASN / 运营商查询，与 {@link IpTimezoneClient} 同一来源（ipwho.is，免密钥、HTTPS），
 * 只取 connection.asn 与 connection.isp。
 * 另起一个客户端而不是给 IpTimezoneClient 加方法：那个类名只管时区，塞进 ASN 查询会名不副实，
 * 且会牵动 EgressCheckService。不加 @Component——由 DnsConfig 的 @Bean 装配。
 */
@Slf4j
public class RestClientIpAsnClient implements IpAsnClient {

    /**
     * asn 与 isp 在**同一次响应**里一起取回：运营商名是上报落库要用的（link_report.isp），
     * 为它再打一次 ipwho.is 会让高频的心跳路径凭空多出一倍外部调用
     */
    private static final String LOOKUP_URL =
            "https://ipwho.is/{ip}?fields=success,connection.asn,connection.isp";

    private final RestClient restClient;

    public RestClientIpAsnClient(RestClient geoIpRestClient) {
        this.restClient = geoIpRestClient;
    }

    @Override
    public Optional<AsnInfo> lookup(String ip) {
        try {
            Map<?, ?> body = restClient.get().uri(LOOKUP_URL, ip).retrieve().body(Map.class);
            if (body == null || !Boolean.TRUE.equals(body.get("success"))
                    || !(body.get("connection") instanceof Map<?, ?> connection)
                    || !(connection.get("asn") instanceof Number asn)) {
                log.warn("ASN 查不到 ip={} body={}", ip, body);
                return Optional.empty();
            }
            // isp 缺失或空白一律归一成 null：把空白串当运营商名传下去，会在矩阵上多出一列
            // 看不出是什么的空表头，也会让告警文案出现「运营商 " " 成功率异常」
            String isp = connection.get("isp") instanceof String raw && !raw.isBlank() ? raw.trim() : null;
            return Optional.of(new AsnInfo("AS" + asn.intValue(), isp));
        } catch (Exception e) {
            log.warn("ASN 查询失败 ip={}", ip, e);
            return Optional.empty();
        }
    }
}
