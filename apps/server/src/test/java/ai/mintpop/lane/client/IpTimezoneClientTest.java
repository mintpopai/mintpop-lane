package ai.mintpop.lane.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IpTimezoneClientTest {

    private static final String URL = "https://ipwho.is/203.0.113.9?fields=success,timezone.id";

    private MockRestServiceServer server;
    private IpTimezoneClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IpTimezoneClient(builder.build());
    }

    @Test
    @DisplayName("GET ipwho.is 只取 success 与 timezone.id，返回合法 IANA 时区名")
    void returnsTimezoneId() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"success\":true,\"timezone\":{\"id\":\"Asia/Tokyo\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).contains("Asia/Tokyo");
        server.verify();
    }

    @Test
    @DisplayName("success=false（查不到 / 限流）：返回空")
    void emptyWhenNotSuccess() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"Reserved range\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).isEmpty();
    }

    @Test
    @DisplayName("返回的不是合法 IANA 时区名：返回空，不把坏值往下传")
    void emptyWhenTimezoneInvalid() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":true,\"timezone\":{\"id\":\"东京时间\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).isEmpty();
    }

    @Test
    @DisplayName("HTTP 层错误：返回空，不抛")
    void emptyOnHttpError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(client.lookup("203.0.113.9")).isEmpty();
    }
}
