package ai.mintpop.lane.client;

import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
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
    void 带clashUA拉取() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andExpect(header(HttpHeaders.USER_AGENT, "clash.meta"))
                .andRespond(withSuccess("proxies: []", MediaType.TEXT_PLAIN));

        assertThat(client.fetch("https://sub.example.com/c?token=t")).isEqualTo("proxies: []");
    }

    @Test
    @DisplayName("订阅端非 2xx 时报订阅拉取失败，而不是把异常裸抛成 500")
    void 非2xx报拉取失败() {
        server.expect(requestTo("https://sub.example.com/c?token=t")).andRespond(withServerError());

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/c?token=t"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("响应体为空时报拉取失败")
    void 空响应报拉取失败() {
        server.expect(requestTo("https://sub.example.com/empty"))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/empty"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("链接不是合法 URL 时报拉取失败")
    void 坏链接报拉取失败() {
        assertThatThrownBy(() -> client.fetch("不是链接"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }

    @Test
    @DisplayName("网络 I/O 异常（超时/DNS/连接拒绝等）时同样报拉取失败——" +
            "这类异常被 RestClient 包成 ResourceAccessException，message 里内嵌完整原始 URL（含 token），" +
            "本用例只兜行为，不断言日志内容")
    void 网络IO异常报拉取失败() {
        server.expect(requestTo("https://sub.example.com/c?token=t"))
                .andRespond(request -> {
                    throw new IOException("模拟网络故障");
                });

        assertThatThrownBy(() -> client.fetch("https://sub.example.com/c?token=t"))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.SUB_FETCH_FAILED);
    }
}
