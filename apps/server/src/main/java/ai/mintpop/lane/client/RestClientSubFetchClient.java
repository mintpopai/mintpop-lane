package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 订阅拉取的 RestClient 实现。 */
@Slf4j
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
        } catch (HttpClientErrorException.TooManyRequests e) {
            Duration retryAfter = parseRetryAfter(e.getResponseHeaders());
            log.warn("订阅拉取被限流 429，url={}，Retry-After={}", maskUrl(subUrl), retryAfter);
            throw new SubFetchRateLimitedException(retryAfter);
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
        // 机场名与流量额度是两个互不相干的可选头，各自独立降级：一个解析失败不影响另一个
        String airportName = parseAirportName(response.getHeaders());
        return parseUserinfo(body, airportName, response.getHeaders());
    }

    /**
     * 解析 content-disposition 头里的 filename*（RFC 5987：{@code <charset>'<language>'<百分号编码值>}），
     * 拿到机场在订阅里给的展示名。**解析失败一律降级为 null，绝不抛异常**——头缺失、没有 filename*、
     * 编码不认识都不能挡住订阅拉取本身。用 Spring 自带的 {@link ContentDisposition} 做 RFC 5987 解码，
     * 不手写百分号解码。
     */
    private String parseAirportName(HttpHeaders headers) {
        try {
            String header = headers.getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (header == null) {
                return null;
            }
            String filename = ContentDisposition.parse(header).getFilename();
            return (filename == null || filename.isBlank()) ? null : filename;
        } catch (Exception e) {
            log.warn("content-disposition 解析失败，降级为无机场名，原因={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 解析 subscription-userinfo 头，拼出额度元信息。**解析失败一律降级为 null，绝不抛异常**——
     * 额度是附带信息，不是所有机场都提供，缺了或格式不认识都不能挡住订阅拉取本身。
     */
    private SubFetchResult parseUserinfo(String body, String airportName, HttpHeaders headers) {
        try {
            String header = headers.getFirst(USERINFO_HEADER);
            if (header == null) {
                return new SubFetchResult(body, airportName, null, null, null);
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
            // expire=0（有些机场用它表示「不限期」）与负数都不是真实到期时间，归一成 null。
            // 直接 ofEpochSecond 会得到 1970-01-01，到期告警会把它当成「早已过期」每轮刷屏
            Instant expiresAt = (expire != null && expire > 0) ? Instant.ofEpochSecond(expire) : null;
            return new SubFetchResult(body, airportName, used, total, expiresAt);
        } catch (Exception e) {
            log.warn("subscription-userinfo 头解析失败，降级为无额度信息，原因={}", e.getClass().getSimpleName());
            return new SubFetchResult(body, airportName, null, null, null);
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

    /** 只认秒数形式的 Retry-After；HTTP 日期形式或缺失一律 null，交给重试层用固定退避 */
    private static Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String raw = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (raw == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(raw.trim()));
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
