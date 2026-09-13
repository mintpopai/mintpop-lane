package ai.mintpop.lane.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * 出口 IP → IANA 时区的 GeoIP 查询，与管理端表单预填用同一来源（ipwho.is，免密钥、HTTPS），只取 timezone.id。
 * 任何失败（网络不通、限流、查不到、返回的不是合法时区名）一律返回空并记日志，由调用方决定是中止还是跳过：
 * 出口 IP 与时区必须成对写入，解析不到时区就不能只写 IP。
 */
@Slf4j
@Component
public class IpTimezoneClient {

    private static final String LOOKUP_URL = "https://ipwho.is/{ip}?fields=success,timezone.id";

    private final RestClient restClient;

    public IpTimezoneClient(RestClient geoIpRestClient) {
        this.restClient = geoIpRestClient;
    }

    public Optional<String> lookup(String ip) {
        try {
            Map<?, ?> body = restClient.get().uri(LOOKUP_URL, ip).retrieve().body(Map.class);
            if (body == null || !Boolean.TRUE.equals(body.get("success"))
                    || !(body.get("timezone") instanceof Map<?, ?> timezone)
                    || !(timezone.get("id") instanceof String id) || id.isBlank()) {
                log.warn("GeoIP 查不到时区 ip={} body={}", ip, body);
                return Optional.empty();
            }
            ZoneId.of(id);
            return Optional.of(id);
        } catch (DateTimeException e) {
            log.warn("GeoIP 返回的不是合法 IANA 时区名 ip={}", ip, e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("GeoIP 查询失败 ip={}", ip, e);
            return Optional.empty();
        }
    }
}
