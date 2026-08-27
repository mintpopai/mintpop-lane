package ai.mintpop.lane.client;

import ai.mintpop.lane.client.ClaudeOAuthClient.ExchangeCommand;
import ai.mintpop.lane.client.ClaudeOAuthClient.TokenResult;
import ai.mintpop.lane.config.ClaudeOAuthProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ClaudeOAuthClientTest {

    private MockRestServiceServer server;
    private ClaudeOAuthProperties properties;
    private ClaudeOAuthClient client;
    /** 出站经哪个落地节点在这里无关紧要，只需一个非 null 的占位对象 */
    private final ProxyNodeDto land = new ProxyNodeDto();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        properties = new ClaudeOAuthProperties();

        LandProxyClientFactory clientFactory = mock(LandProxyClientFactory.class);
        when(clientFactory.create(any())).thenReturn(restClient);

        client = new ClaudeOAuthClient(properties, clientFactory, new ObjectMapper());
    }

    @Test
    @DisplayName("授权链接手工拼接：参数顺序与 CLI 一致，scope 空格编码为 + 而非 %20")
    void buildAuthorizeUrlEncodesScopeWithPlus() {
        String url = client.buildAuthorizeUrl("state-1", "challenge-1", "user:profile user:inference");

        assertThat(url).isEqualTo(
                "https://claude.com/cai/oauth/authorize"
                        + "?code=true"
                        + "&client_id=9d1c250a-e61b-44d9-88ed-5944d1962f5e"
                        + "&response_type=code"
                        + "&redirect_uri=https%3A%2F%2Fplatform.claude.com%2Foauth%2Fcode%2Fcallback"
                        + "&scope=user%3Aprofile+user%3Ainference"
                        + "&code_challenge=challenge-1"
                        + "&code_challenge_method=S256"
                        + "&state=state-1");
    }

    @Test
    @DisplayName("兑换成功：请求体、请求头符合约定，响应体正确解析为 TokenResult")
    void exchangeSucceeds() {
        server.expect(requestTo("https://platform.claude.com/v1/oauth/token"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.USER_AGENT, "axios/1.13.6"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/json, text/plain, */*"))
                .andExpect(content().json("""
                        {
                          "grant_type": "authorization_code",
                          "code": "auth-code",
                          "state": "state-1",
                          "client_id": "9d1c250a-e61b-44d9-88ed-5944d1962f5e",
                          "redirect_uri": "https://platform.claude.com/oauth/code/callback",
                          "code_verifier": "verifier-1",
                          "expires_in": 31536000
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "access_token": "at-1",
                          "refresh_token": "rt-1",
                          "scope": "user:profile user:inference",
                          "expires_in": 31536000,
                          "issued_at": 1735689600,
                          "token_uuid": "uuid-1"
                        }
                        """, MediaType.APPLICATION_JSON));

        TokenResult result = client.exchange(land,
                new ExchangeCommand("auth-code", "state-1", "verifier-1", 31536000L));

        assertThat(result.accessToken()).isEqualTo("at-1");
        assertThat(result.refreshToken()).isEqualTo("rt-1");
        assertThat(result.scope()).isEqualTo("user:profile user:inference");
        assertThat(result.expiresIn()).isEqualTo(31536000L);
        assertThat(result.issuedAt()).isEqualTo(Instant.ofEpochSecond(1735689600L));
        assertThat(result.tokenUuid()).isEqualTo("uuid-1");
    }

    @Test
    @DisplayName("响应体缺 issued_at 时回退为当前时间，而不是抛异常")
    void exchangeFallsBackIssuedAtWhenMissing() {
        server.expect(requestTo("https://platform.claude.com/v1/oauth/token"))
                .andRespond(withSuccess("""
                        {"access_token": "at-1", "refresh_token": "rt-1", "scope": "user:profile user:inference", "expires_in": 3600, "token_uuid": "uuid-1"}
                        """, MediaType.APPLICATION_JSON));

        Instant before = Instant.now();
        TokenResult result = client.exchange(land,
                new ExchangeCommand("auth-code", "state-1", "verifier-1", 3600L));
        Instant after = Instant.now();

        assertThat(result.issuedAt()).isBetween(before.minus(1, ChronoUnit.SECONDS), after.plus(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("上游返回非 2xx 时报凭证兑换失败，而不是把原始错误裸抛出去")
    void exchangeFailureReportsCredentialExchangeFailed() {
        server.expect(requestTo("https://platform.claude.com/v1/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("""
                                {"error": "invalid_grant", "error_description": "Custom expires_in not allowed for scope 'user:mcp_servers'"}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.exchange(land,
                new ExchangeCommand("bad-code", "state-1", "verifier-1", 31536000L)))
                .isInstanceOf(BizException.class)
                .extracting("bizCode").isEqualTo(BizCodeEnum.CREDENTIAL_EXCHANGE_FAILED);
    }

    @Test
    @DisplayName("吊销成功：请求体符合约定，返回 true")
    void revokeSucceeds() {
        server.expect(requestTo("https://platform.claude.com/v1/oauth/token/revoke"))
                .andExpect(content().json("""
                        {
                          "token": "at-1",
                          "token_type_hint": "access_token",
                          "client_id": "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
                        }
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.revoke(land, "at-1")).isTrue();
    }

    @Test
    @DisplayName("吊销失败：吞掉异常返回 false，不向上抛")
    void revokeFailureReturnsFalse() {
        server.expect(requestTo("https://platform.claude.com/v1/oauth/token/revoke"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.revoke(land, "at-1")).isFalse();
    }
}
