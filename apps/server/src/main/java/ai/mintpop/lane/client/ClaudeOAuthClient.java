package ai.mintpop.lane.client;

import ai.mintpop.lane.config.ClaudeOAuthProperties;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/** 与 Anthropic OAuth 端点的全部交互。所有出站请求都经调用方指定的落地节点。 */
@Slf4j
@Component
public class ClaudeOAuthClient {

    /** 兑换凭证的入参 */
    public record ExchangeCommand(String code, String state, String codeVerifier, long expiresIn) {
    }

    /** 兑换结果 */
    public record TokenResult(String accessToken, String refreshToken, String scope,
                               long expiresIn, Instant issuedAt, String tokenUuid) {
    }

    /** 与 CLI 一致：token 端点的请求由 axios 发出 */
    private static final String TOKEN_USER_AGENT = "axios/1.13.6";

    private final ClaudeOAuthProperties properties;
    private final LandProxyClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    public ClaudeOAuthClient(ClaudeOAuthProperties properties,
                              LandProxyClientFactory clientFactory,
                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.clientFactory = clientFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * 拼授权链接。参数顺序与 CLI 逐一对应，scope 内的空格编码为 +
     * （CLI 与 mintpop-api 都是手工拼接而非用 URI builder，此处保持一致）。
     */
    public String buildAuthorizeUrl(String state, String challenge, String scope) {
        return properties.getAuthorizeUrl()
                + "?code=true"
                + "&client_id=" + properties.getClientId()
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(properties.getRedirectUri(), StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8).replace("%20", "+")
                + "&code_challenge=" + challenge
                + "&code_challenge_method=S256"
                + "&state=" + state;
    }

    /** 用授权码换凭证，出站经指定落地节点 */
    public TokenResult exchange(ProxyNodeDto land, ExchangeCommand command) {
        Map<String, Object> body = Map.of(
                "grant_type", "authorization_code",
                "code", command.code(),
                "state", command.state(),
                "client_id", properties.getClientId(),
                "redirect_uri", properties.getRedirectUri(),
                "code_verifier", command.codeVerifier(),
                "expires_in", command.expiresIn()
        );
        JsonNode json = postJson(land, properties.getTokenUrl(), body, BizCodeEnum.CREDENTIAL_EXCHANGE_FAILED);
        long expiresIn = json.path("expires_in").asLong(0);
        Instant issuedAt = json.hasNonNull("issued_at")
                ? Instant.ofEpochSecond(json.get("issued_at").asLong())
                : Instant.now();
        return new TokenResult(
                json.path("access_token").asText(""),
                json.path("refresh_token").asText(""),
                json.path("scope").asText(""),
                expiresIn,
                issuedAt,
                json.path("token_uuid").asText("")
        );
    }

    /**
     * 吊销凭证（RFC 7009）。
     * ⚠️ 以 access_token 吊销尚未实测，见设计文档 §9.2。失败不抛异常，由调用方按返回值决定告警。
     */
    public boolean revoke(ProxyNodeDto land, String token) {
        try {
            postJson(land, properties.getRevokeUrl(), Map.of(
                    "token", token,
                    "token_type_hint", "access_token",
                    "client_id", properties.getClientId()
            ), BizCodeEnum.CREDENTIAL_REVOKE_FAILED);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode postJson(ProxyNodeDto land, String url, Map<String, Object> body, BizCodeEnum onFailure) {
        try {
            String response = clientFactory.create(land)
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", TOKEN_USER_AGENT)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            return response == null || response.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(response);
        } catch (RestClientResponseException e) {
            // 上游错误正文里有排障关键信息（例如「某 scope 不允许自定义 expires_in」），
            // 必须落日志；但不放进 BizException 的 message——那会原样回显到管理端前端
            log.warn("Claude OAuth 请求失败 url={} status={} body={}",
                    url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BizException(onFailure);
        } catch (Exception e) {
            log.warn("Claude OAuth 请求异常 url={}", url, e);
            throw new BizException(onFailure);
        }
    }
}
