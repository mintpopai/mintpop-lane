package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/** 订阅拉取的 RestClient 实现。 */
@Component
public class RestClientSubFetchClient implements SubFetchClient {

    /** mihomo 系 UA：订阅端据此返回 Clash YAML 而非 base64 URI 列表，解析器只认前者 */
    static final String CLASH_UA = "clash.meta";

    private final RestClient restClient;

    public RestClientSubFetchClient(RestClient subRestClient) {
        this.restClient = subRestClient;
    }

    @Override
    public String fetch(String subUrl) {
        String body;
        try {
            // URI.create 而非 uri(String)：后者会把 {} 当模板变量展开，订阅链接里出现花括号会炸
            URI uri = URI.create(subUrl);
            // 验证 URI 是否为绝对 URI（必须包含 scheme），否则作为无效 URL 处理
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("URL must be absolute");
            }
            body = restClient.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, CLASH_UA)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
        if (body == null || body.isBlank()) {
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
        return body;
    }
}
