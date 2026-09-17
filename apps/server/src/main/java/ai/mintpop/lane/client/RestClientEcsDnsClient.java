package ai.mintpop.lane.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 走 Google Public DNS-over-HTTPS 的 A 记录解析，带 edns_client_subnet 参数以获得分线路结果。
 * 不加 @Component——由 DnsConfig 的 @Bean 装配（与 JndiFailureDomainResolver 同一模式）。
 */
@Slf4j
public class RestClientEcsDnsClient implements EcsDnsClient {

    private static final String RESOLVE_URL =
            "https://dns.google/resolve?name={host}&type=A&edns_client_subnet={subnet}";

    /** dns.google 应答里 A 记录的 type 值（RFC 1035） */
    private static final int RECORD_TYPE_A = 1;

    private final RestClient restClient;

    public RestClientEcsDnsClient(RestClient dnsRestClient) {
        this.restClient = dnsRestClient;
    }

    @Override
    public List<String> resolveA(String host, String clientSubnet) {
        try {
            Map<?, ?> body = restClient.get().uri(RESOLVE_URL, host, clientSubnet).retrieve().body(Map.class);
            if (body == null || !(body.get("Answer") instanceof List<?> answers)) {
                return List.of();
            }
            List<String> ips = new ArrayList<>();
            for (Object item : answers) {
                if (item instanceof Map<?, ?> answer
                        && answer.get("type") instanceof Number type && type.intValue() == RECORD_TYPE_A
                        && answer.get("data") instanceof String ip && !ip.isBlank()) {
                    ips.add(ip);
                }
            }
            return ips;
        } catch (Exception e) {
            log.warn("ECS DNS 解析失败 host={} subnet={} 原因={}", host, clientSubnet, e.getClass().getSimpleName());
            return List.of();
        }
    }
}
