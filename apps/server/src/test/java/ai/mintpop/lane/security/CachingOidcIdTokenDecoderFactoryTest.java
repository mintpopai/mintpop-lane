package ai.mintpop.lane.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录回调里校验 id_token 要拉 Logto 的 JWKS。框架默认的解码器工厂有两处与生产网络不相容：
 * 每次登录新建解码器（JWKS 缓存随之作废、次次现拉），且拉取的连接 / 读超时只有 Nimbus 默认的 500ms。
 * 实测本机到 auth.mintpop.dev 一次完整请求 0.86~1.08s，冷连接首拉必超时，登录于是报「登录未能完成」。
 * 这里用一个延迟响应的本地 JWKS 服务把这两点钉住。
 */
@DisplayName("CachingOidcIdTokenDecoderFactory：id_token 验签的 JWKS 拉取")
class CachingOidcIdTokenDecoderFactoryTest {

    /** 慢于 Nimbus 默认的 500ms 读超时、又在生产配置的超时之内 */
    private static final long JWKS_DELAY_MS = 1_500;
    private static final String CLIENT_ID = "lane-web";

    private HttpServer jwksServer;
    private final AtomicInteger jwksHits = new AtomicInteger();
    private ECKey signingKey;
    private String issuer;
    private ClientRegistration registration;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = new ECKeyGenerator(Curve.P_384).keyID("k1").generate();
        byte[] jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/oidc/jwks", exchange -> {
            jwksHits.incrementAndGet();
            try {
                Thread.sleep(JWKS_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        jwksServer.start();

        issuer = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/oidc";
        registration = ClientRegistration.withRegistrationId("logto")
                .clientId(CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/logto")
                .authorizationUri(issuer + "/auth")
                .tokenUri(issuer + "/token")
                .jwkSetUri(issuer + "/jwks")
                .issuerUri(issuer)
                .build();
    }

    @AfterEach
    void tearDown() {
        jwksServer.stop(0);
    }

    private String idToken(String subject) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(List.of(CLIENT_ID))
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES384).keyID("k1").build(), claims);
        jwt.sign(new ECDSASigner(signingKey));
        return jwt.serialize();
    }

    @Test
    @DisplayName("JWKS 响应慢于 500ms（仍在超时之内）时照样验签通过，不把登录判失败")
    void toleratesSlowJwksEndpoint() throws Exception {
        JwtDecoder decoder = new CachingOidcIdTokenDecoderFactory(SignatureAlgorithm.ES384)
                .createDecoder(registration);

        Jwt jwt = decoder.decode(idToken("user-1"));

        assertThat(jwt.getSubject()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("同一 client 的解码器跨登录复用：连续两次登录只拉一次 JWKS")
    void reusesDecoderAcrossLogins() throws Exception {
        CachingOidcIdTokenDecoderFactory factory = new CachingOidcIdTokenDecoderFactory(SignatureAlgorithm.ES384);

        factory.createDecoder(registration).decode(idToken("user-1"));
        factory.createDecoder(registration).decode(idToken("user-2"));

        assertThat(jwksHits).hasValue(1);
    }
}
