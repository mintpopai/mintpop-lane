package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 订阅拉取的 RestClient 实现。 */
@Slf4j
@Component
public class RestClientSubFetchClient implements SubFetchClient {

    /** mihomo 系 UA：订阅端据此返回 Clash YAML 而非 base64 URI 列表，解析器只认前者 */
    static final String CLASH_UA = "clash.meta";

    /** 机场吐流量额度的约定头：upload=<字节>; download=<字节>; total=<字节>; expire=<epoch 秒> */
    private static final String USERINFO_HEADER = "subscription-userinfo";

    private final RestClient restClient;

    public RestClientSubFetchClient(RestClient subRestClient) {
        this.restClient = subRestClient;
    }

    @Override
    public SubFetchResult fetch(String subUrl) {
        ResponseEntity<String> response;
        try {
            // URI.create 而非 uri(String)：后者会把 {} 当模板变量展开，订阅链接里出现花括号会炸
            URI uri = URI.create(subUrl);
            // 验证 URI 是否为绝对 URI（必须包含 scheme），否则作为无效 URL 处理
            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("URL must be absolute");
            }
            // 用 toEntity 而非 body：额度信息在响应头里，body(Class) 拿不到 ResponseEntity
            response = restClient.get().uri(uri)
                    .header(HttpHeaders.USER_AGENT, CLASH_UA)
                    .retrieve()
                    .toEntity(String.class);
        } catch (RestClientException | IllegalArgumentException e) {
            // 异常原因不能吞，但 e.getMessage() 不能打：Spring 的 ResourceAccessException
            // （超时/DNS 失败/连接拒绝——恰恰是最常见的失败路径）message 形如
            // `I/O error on GET request for "<完整URI>": ...`，内嵌完整原始 URL，
            // token 若在 path/query 里就会绕开下面的 maskUrl(subUrl) 直接进日志。
            // 异常类名已足够区分超时/DNS/4xx/5xx 等大类，故日志只记类名，不记 message。
            log.warn("订阅拉取失败，url={}，原因={}", maskUrl(subUrl), e.getClass().getSimpleName());
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            log.warn("订阅拉取返回空响应体，url={}", maskUrl(subUrl));
            throw new BizException(BizCodeEnum.SUB_FETCH_FAILED);
        }
        return parseUserinfo(body, response.getHeaders());
    }

    /**
     * 解析 subscription-userinfo 头，拼出额度元信息。**解析失败一律降级为 null，绝不抛异常**——
     * 额度是附带信息，不是所有机场都提供，缺了或格式不认识都不能挡住订阅拉取本身。
     */
    private SubFetchResult parseUserinfo(String body, HttpHeaders headers) {
        try {
            String header = headers.getFirst(USERINFO_HEADER);
            if (header == null) {
                return new SubFetchResult(body, null, null, null);
            }
            Map<String, String> fields = new HashMap<>();
            for (String segment : header.split(";")) {
                String[] kv = segment.trim().split("=", 2);
                if (kv.length == 2) {
                    fields.put(kv[0].trim(), kv[1].trim());
                }
            }
            Long upload = parseLong(fields.get("upload"));
            Long download = parseLong(fields.get("download"));
            Long total = parseLong(fields.get("total"));
            Long expire = parseLong(fields.get("expire"));
            Long used = (upload != null && download != null) ? upload + download : null;
            Instant expiresAt = expire != null ? Instant.ofEpochSecond(expire) : null;
            return new SubFetchResult(body, used, total, expiresAt);
        } catch (Exception e) {
            log.warn("subscription-userinfo 头解析失败，降级为无额度信息，原因={}", e.getClass().getSimpleName());
            return new SubFetchResult(body, null, null, null);
        }
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 日志用打码链接：只留 scheme 与 host，token 一律不写日志 */
    private String maskUrl(String subUrl) {
        try {
            URI uri = URI.create(subUrl);
            return uri.getScheme() + "://" + uri.getHost() + "/…";
        } catch (Exception e) {
            return "（无法解析的链接）";
        }
    }
}
