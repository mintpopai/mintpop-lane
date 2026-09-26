package ai.mintpop.lane.client;

import ai.mintpop.lane.client.IpAsnClient.AsnInfo;
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

/**
 * ipwho.is 的 ASN / 运营商反查。
 * <p>
 * 这个类此前没有直接单测，而三期把它从「低频尽调」提到了「每条上报都要用」的位置：
 * {@code link_report.source_asn}（故障域 × 运营商矩阵与运营商级告警的唯一数据来源）就靠这里，
 * 运营商的展示名（{@code asn_org.org_name}）也从同一次响应里多取一个 {@code connection.isp} 字段。
 * 反查整体失败时整个运营商维度会退化成「未知运营商」一行。
 */
class RestClientIpAsnClientTest {

    /** 出站 URI 的确切形状：一次请求同时取 asn 与 isp，不为了运营商名再打一次 ipwho.is */
    private static final String URL = "https://ipwho.is/203.0.113.9?fields=success,connection.asn,connection.isp";

    private MockRestServiceServer server;
    private RestClientIpAsnClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestClientIpAsnClient(builder.build());
    }

    @Test
    @DisplayName("一次响应同时取到 ASN 与运营商名")
    void returnsAsnAndIspFromSingleResponse() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"success\":true,\"connection\":{\"asn\":4134,\"isp\":\"China Telecom\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9"))
                .contains(new AsnInfo("AS4134", "China Telecom"));
        server.verify();
    }

    @Test
    @DisplayName("应答里没有 connection.isp 时 isp 为 null，ASN 照常取到——运营商名是可选的，ASN 才是硬要求")
    void ispIsNullWhenMissing() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":true,\"connection\":{\"asn\":4134}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).contains(new AsnInfo("AS4134", null));
    }

    @Test
    @DisplayName("connection.isp 是空白串时归一成 null，不把空白当成运营商名传下去")
    void blankIspIsNormalizedToNull() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":true,\"connection\":{\"asn\":4134,\"isp\":\"   \"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).contains(new AsnInfo("AS4134", null));
    }

    @Test
    @DisplayName("success=false（查不到 / 限流）：返回空，不抛")
    void emptyWhenNotSuccess() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"success\":false,\"message\":\"Reserved range\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).isEmpty();
    }

    @Test
    @DisplayName("非 2xx 降级成空，不抛——调用方按「本次没查到」处理")
    void emptyWhenServerError() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(client.lookup("203.0.113.9")).isEmpty();
    }

    @Test
    @DisplayName("运营商名超过 64 字符时被截到 64 字符——ipwho.is 的 isp 是自由文本组织名，"
            + "超长会撑爆 asn_org.org_name 的 VARCHAR(64) 列，MySQL 严格模式下抛 Data too long")
    void ispLongerThan64CharsIsTruncatedTo64() {
        String longIsp = "China Networks Inter-Exchange, China Telecommunications Corporation";
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"connection\":{\"asn\":4134,\"isp\":\"" + longIsp + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9"))
                .contains(new AsnInfo("AS4134", longIsp.substring(0, 64)));
    }

    @Test
    @DisplayName("运营商名恰好 64 字符时原样保留，不多截一个——边界值不能被差一错误坑")
    void ispExactly64CharsIsKeptAsIs() {
        String isp64 = "A".repeat(64);
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"connection\":{\"asn\":4134,\"isp\":\"" + isp64 + "\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9")).contains(new AsnInfo("AS4134", isp64));
    }

    @Test
    @DisplayName("4 字节 ASN（≥ 2^31）原样拼出，不因按 int 取值而溢出成负数")
    void fourByteAsnIsNotTruncatedToNegativeInt() {
        // ASN 自 RFC 6793 起是 32 位无符号数，上限 4294967295 已超出 Java int 的 2147483647。
        // 按 intValue() 取值会让 4200000000 溢出成 -94967296，拼出「AS-94967296」这种
        // 根本不存在的键：矩阵上多出一列鬼运营商，asn_org 里也按这个假键落一行展示名，
        // 且同一个真实 ASN 在不同上报间不会稳定地溢出成同一个值时还会把它裂成几列。
        // 4200000000 落在 RFC 6996 的私有 ASN 段（4200000000-4294967294），中转链路确实见得到
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"connection\":{\"asn\":4200000000,\"isp\":\"Private ASN\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup("203.0.113.9"))
                .contains(new AsnInfo("AS4200000000", "Private ASN"));
    }

    @Test
    @DisplayName("既有的 lookupAsn 调用方零改动：默认实现委托 lookup 只取 ASN 段")
    void lookupAsnDelegatesToLookup() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess(
                        "{\"success\":true,\"connection\":{\"asn\":16509,\"isp\":\"Amazon.com\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookupAsn("203.0.113.9")).contains("AS16509");
    }
}
