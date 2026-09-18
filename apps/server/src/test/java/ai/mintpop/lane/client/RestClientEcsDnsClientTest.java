package ai.mintpop.lane.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * dns.google 的 DoH 解析。
 * <p>
 * 本类此前没有直接单测，而它带着一个没被验证过的真风险：ECS 子网（{@code 202.96.209.0/24}）
 * 是作为 URI 模板变量塞进 query 的，**里面那个斜杠会被怎么编码从没人确认过**。
 * 一旦 dns.google 不认这个形状，{@code resolveA} 按设计静默返回空列表 →
 * 入口 IP 巡检永远「解析为空，本轮跳过」，整个功能哑掉且不报错、日志里只有一行 warn。
 * 所以第一条用例就是断言出站 URI 的确切形状。
 */
class RestClientEcsDnsClientTest {

    private MockRestServiceServer server;
    private RestClientEcsDnsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientEcsDnsClient(builder.build());
    }

    private static String answer(String type, String data) {
        return "{\"data\":\"" + data + "\",\"type\":" + type + "}";
    }

    @Test
    @DisplayName("出站 URI 的确切形状：子网作为 URI 模板变量展开，斜杠被编码成 %2F（dns.google 实测认这个形状）")
    void sendsExactOutboundUri() {
        // Spring 的 DefaultUriBuilderFactory 默认是 TEMPLATE_AND_VALUES 模式：模板本身按原样，
        // 展开进去的变量值按严格规则编码，于是 "202.96.209.0/24" 里的 / 变成 %2F。
        // 这是合规形态（RFC 3986：query 值里的 %2F 由服务端解码回 /），2026-09-18 对 dns.google
        // 实测两种写法（%2F 与裸 /）返回一致，应答里 edns_client_subnet 都回显成 202.96.209.0/24。
        // 之所以要把它钉成断言：resolveA 的失败路径是**静默返回空列表**，
        // 这个形状一旦哪天被改坏，入口 IP 巡检会永远「解析为空，本轮跳过」，功能哑掉却不报错
        server.expect(requestTo(
                        "https://dns.google/resolve?name=jp.tsdns.top&type=A&edns_client_subnet=202.96.209.0%2F24"))
                .andRespond(withSuccess("{\"Answer\":[" + answer("1", "13.192.233.178") + "]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.resolveA("jp.tsdns.top", "202.96.209.0/24")).containsExactly("13.192.233.178");
    }

    @Test
    @DisplayName("只取 A 记录（type=1），CNAME 等其它记录类型一律跳过")
    void keepsOnlyARecords() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://dns.google/resolve")))
                .andRespond(withSuccess("{\"Answer\":["
                        + answer("5", "jp.tsdns.top.cdn.example.com") + ","
                        + answer("1", "13.192.233.178") + "]}", MediaType.APPLICATION_JSON));

        assertThat(client.resolveA("jp.tsdns.top", "202.96.209.0/24")).containsExactly("13.192.233.178");
    }

    @Test
    @DisplayName("应答里没有 Answer 段时返回空列表，不抛")
    void returnsEmptyWhenNoAnswer() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://dns.google/resolve")))
                .andRespond(withSuccess("{\"Status\":3}", MediaType.APPLICATION_JSON));

        assertThat(client.resolveA("jp.tsdns.top", "202.96.209.0/24")).isEmpty();
    }

    @Test
    @DisplayName("网络异常与非 2xx 一律降级成空列表：调用方（巡检/尽调）按「本轮没查到」处理，不让一次抖动炸掉整轮")
    void degradesToEmptyOnFailure() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://dns.google/resolve")))
                .andRespond(withServerError());
        assertThat(client.resolveA("jp.tsdns.top", "202.96.209.0/24")).isEmpty();

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://dns.google/resolve")))
                .andRespond(request -> {
                    throw new IOException("模拟网络故障");
                });
        assertThat(client.resolveA("jp.tsdns.top", "202.96.209.0/24")).isEmpty();
    }
}
