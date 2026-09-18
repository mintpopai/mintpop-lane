package ai.mintpop.lane.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * IP → ASN 查询，与 {@link IpTimezoneClient} 同一来源（ipwho.is，免密钥、HTTPS），只取 connection.asn。
 * 另起一个客户端而不是给 IpTimezoneClient 加方法：那个类名只管时区，塞进 ASN 查询会名不副实，
 * 且会牵动 EgressCheckService。不加 @Component——由 DnsConfig 的 @Bean 装配。
 */
@Slf4j
public class RestClientIpAsnClient implements IpAsnClient {

    private static final String LOOKUP_URL = "https://ipwho.is/{ip}?fields=success,connection.asn";

    private final RestClient restClient;

    public RestClientIpAsnClient(RestClient geoIpRestClient) {
        this.restClient = geoIpRestClient;
    }

    @Override
    public Optional<String> lookupAsn(String ip) {
        try {
            Map<?, ?> body = restClient.get().uri(LOOKUP_URL, ip).retrieve().body(Map.class);
            if (body == null || !Boolean.TRUE.equals(body.get("success"))
                    || !(body.get("connection") instanceof Map<?, ?> connection)
                    || !(connection.get("asn") instanceof Number asn)) {
                log.warn("ASN 查不到 ip={} body={}", ip, body);
                return Optional.empty();
            }
            return Optional.of("AS" + asn.intValue());
        } catch (Exception e) {
            log.warn("ASN 查询失败 ip={}", ip, e);
            return Optional.empty();
        }
    }
}
