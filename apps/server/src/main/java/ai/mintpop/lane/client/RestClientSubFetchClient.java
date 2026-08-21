package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

/** 订阅拉取的 RestClient 实现。 */
@Slf4j
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
            // 异常原因不能吞，但 e.getMessage() 不能打：Spring 的 ResourceAccessException
            // （超时/DNS 失败/连接拒绝——恰恰是最常见的失败路径）message 形如
            // `I/O error on GET request for "<完整URI>": ...`，内嵌完整原始 URL，
            // token 若在 path/query 里就会绕开下面的 打码(subUrl) 直接进日志。
            // 异常类名已足够区分超时/DNS/4xx/5xx 等大类，故日志只记类名，不记 message。
            log.warn("订阅拉取失败，url={}，原因={}", 打码(subUrl), e.getClass().getSimpleName());
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
        if (body == null || body.isBlank()) {
            log.warn("订阅拉取返回空响应体，url={}", 打码(subUrl));
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
        return body;
    }

    /** 日志用打码链接：只留 scheme 与 host，token 一律不写日志 */
    private String 打码(String subUrl) {
        try {
            URI uri = URI.create(subUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/…";
        } catch (Exception e) {
            return "（无法解析的链接）";
        }
    }
}
