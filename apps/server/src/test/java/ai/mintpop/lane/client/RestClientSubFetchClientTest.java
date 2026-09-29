package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientSubFetchClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private RestClientSubFetchClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientSubFetchClient(builder.build());
    }

    @Test
    @DisplayName("带 clash UA 拉取并原样返回响应体——UA 决定订阅端吐 YAML 而不是 base64")
    void fetchWithClashUserAgent() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andExpect(header(HttpHeaders.USER_AGENT, "clash.meta"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN));

        assertThat(client.fetch("https://sub.example.com/c?token=t").body()).isEqualTo("proxies: []");
    }

    @Test
    @DisplayName("订阅端非 2xx 时报订阅拉取失败，而不是把异常裸抛成 500")
    void non2xxReportsFetchFailure() {
        server.expect(requestTo("https://sub.example.com/c?token=t")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/c?token=t"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("响应体为空时报拉取失败")
    void emptyBodyReportsFetchFailure() {
        server.expect(requestTo("https://sub.example.com/empty"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/empty"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("链接不是合法 URL 时报拉取失败")
    void malformedUrlReportsFetchFailure() {
        assertThatThrownBy(() -> client.fetch("不是链接"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("网络 I/O 异常（超时/DNS/连接拒绝等）时同样报拉取失败——" +
            "这类异常被 RestClient 包成 ResourceAccessException，message 里内嵌完整原始 URL（含 token），" +
            "本用例只兜行为，不断言日志内容")
    void networkIoErrorReportsFetchFailure() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(request -> {
                    throw new IOException("模拟网络故障");
                });

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/c?token=t"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("解析 subscription-userinfo 头，已用流量是上传与下载之和")
    void parsesSubscriptionUserinfo() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN)
                        .header("subscription-userinfo",
                                "upload=25219803197; download=10948589634; total=137438953472; expire=1809245089"));

        SubFetchResult result = client.fetch("https://sub.example.com/c?token=t");

        assertThat(result.body()).isEqualTo("proxies: []");
        assertThat(result.usedBytes()).isEqualTo(25219803197L + 10948589634L);
        assertThat(result.totalBytes()).isEqualTo(137438953472L);
        assertThat(result.expiresAt()).isEqualTo(Instant.ofEpochSecond(1809245089L));
    }

    @Test
    @DisplayName("缺 subscription-userinfo 头时三个字段为 null，不影响拉取")
    void toleratesMissingHeader() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN));

        SubFetchResult result = client.fetch("https://sub.example.com/c?token=t");

        assertThat(result.body()).isNotBlank();
        assertThat(result.usedBytes()).isNull();
        assertThat(result.totalBytes()).isNull();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    @DisplayName("头格式不认识时同样降级为 null，不抛异常")
    void toleratesMalformedHeader() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN)
                        .header("subscription-userinfo", "garbage"));

        assertThat(client.fetch("https://sub.example.com/c?token=t").usedBytes()).isNull();
    }

    @Test
    @DisplayName("解析 content-disposition 的 filename*（RFC 5987 百分号编码）得到机场名")
    void parsesAirportNameFromContentDisposition() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN)
                        .header("content-disposition", "attachment;filename*=UTF-8''TaiShan%20Net"));

        assertThat(client.fetch("https://sub.example.com/c?token=t").airportName()).isEqualTo("TaiShan Net");
    }

    @Test
    @DisplayName("缺 content-disposition 头时机场名为 null，不影响拉取")
    void toleratesMissingContentDisposition() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN));

        SubFetchResult result = client.fetch("https://sub.example.com/c?token=t");

        assertThat(result.body()).isNotBlank();
        assertThat(result.airportName()).isNull();
    }

    @Test
    @DisplayName("expire=0（部分机场用它表示不限期）归一成 null，不是 1970-01-01")
    void treatsZeroExpireAsUnlimited() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN)
                        .header("subscription-userinfo",
                                "upload=1; download=2; total=100; expire=0"));

        SubFetchResult result = client.fetch("https://sub.example.com/c?token=t");

        // 不归一的话到期告警会把它当成「早已过期」，每轮刷新推一条，变成纯误报源
        assertThat(result.expiresAt()).isNull();
        assertThat(result.totalBytes()).isEqualTo(100L);
    }

    @Test
    @DisplayName("expire 为负数同样归一成 null，不当成真实到期时间")
    void treatsNegativeExpireAsUnlimited() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN)
                        .header("subscription-userinfo", "upload=1; download=2; total=100; expire=-1"));

        assertThat(client.fetch("https://sub.example.com/c?token=t").expiresAt()).isNull();
    }

    @Test
    @DisplayName("429 抛 SubFetchRateLimitedException 并带上 Retry-After 秒数")
    void tooManyRequestsCarriesRetryAfter() {
        server.expect(requestTo("https://sub.example.com/c?token=x"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "12"));

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/c?token=x"))
                .isInstanceOf(SubFetchRateLimitedException.class)
                .extracting(e -> ((SubFetchRateLimitedException) e).getRetryAfter())
                .isEqualTo(Duration.ofSeconds(12));
    }
}
