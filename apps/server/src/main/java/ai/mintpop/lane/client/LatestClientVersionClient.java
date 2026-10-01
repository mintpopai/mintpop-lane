package ai.mintpop.lane.client;

import ai.mintpop.lane.config.ClientVersionProperties;
import ai.mintpop.lane.util.ClientVersion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * 读桌面端更新清单里的最新版本号。任何失败（网络不通、非 2xx、清单形状不对）一律返回空并记日志，
 * 由 ClientVersionService 沿用上一次拿到的值——清单源抖一下不该把所有在线用户拦在门外。
 */
@Slf4j
@Component
public class LatestClientVersionClient {

    private final RestClient restClient;
    private final ClientVersionProperties properties;

    public LatestClientVersionClient(RestClient clientVersionRestClient, ClientVersionProperties properties) {
        this.restClient = clientVersionRestClient;
        this.properties = properties;
    }

    public Optional<ClientVersion> fetchLatest() {
        try {
            Map<?, ?> body = restClient.get().uri(properties.getManifestUrl()).retrieve().body(Map.class);
            Object raw = body == null ? null : body.get("version");
            Optional<ClientVersion> version = raw instanceof String s ? ClientVersion.parse(s) : Optional.empty();
            if (version.isEmpty()) {
                log.warn("桌面端更新清单里没有可识别的版本号 url={} version={}", properties.getManifestUrl(), raw);
            }
            return version;
        } catch (Exception e) {
            log.warn("拉取桌面端更新清单失败 url={}", properties.getManifestUrl(), e);
            return Optional.empty();
        }
    }
}
