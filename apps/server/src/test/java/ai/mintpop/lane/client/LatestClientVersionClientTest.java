package ai.mintpop.lane.client;

import ai.mintpop.lane.config.ClientVersionProperties;
import ai.mintpop.lane.util.ClientVersion;
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

class LatestClientVersionClientTest {

    private static final String URL = "https://dl.example.com/lane/latest.json";

    private MockRestServiceServer server;
    private LatestClientVersionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ClientVersionProperties properties = new ClientVersionProperties();
        properties.setManifestUrl(URL);
        client = new LatestClientVersionClient(builder.build(), properties);
    }

    @Test
    @DisplayName("GET 配置的清单地址，取出 version")
    void readsVersionFromManifest() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"version":"1.1.0","notes":"","pub_date":"2026-09-30T19:35:42Z","platforms":{}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchLatest()).contains(new ClientVersion(1, 1, 0, null));
        server.verify();
    }

    @Test
    @DisplayName("清单里没有 version 或形状不对：返回空")
    void emptyWhenVersionMissingOrMalformed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"platforms\":{}}", MediaType.APPLICATION_JSON));
        assertThat(client.fetchLatest()).isEmpty();

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"version\":\"latest\"}", MediaType.APPLICATION_JSON));
        assertThat(client.fetchLatest()).isEmpty();
    }

    @Test
    @DisplayName("HTTP 层错误：返回空，不抛")
    void emptyOnHttpError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(client.fetchLatest()).isEmpty();
    }
}
