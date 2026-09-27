package ai.mintpop.lane.security;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * id_token 验签解码器工厂，替代框架默认的 {@link OidcIdTokenDecoderFactory}，修两处与生产网络不相容的默认：
 * <ol>
 *   <li><b>每次登录新建解码器</b>：Spring Security 7 的默认工厂不缓存，JWKS 缓存（Nimbus 默认 5 分钟）
 *       随解码器一起被丢弃，于是次次登录都要现拉一次 JWKS。这里按 registrationId 缓存解码器，缓存才真正生效；
 *       Logto 轮换密钥时，Nimbus 遇到未知 kid 会自行重拉，不需要清这份缓存。</li>
 *   <li><b>拉 JWKS 的连接 / 读超时只有 500ms</b>（Nimbus 默认）：实测本机到 auth.mintpop.dev 一次完整请求
 *       0.86~1.08s，冷连接光 TLS 握手就约 0.55s，首拉几乎必超时，登录回调判失败、前端落到「登录未能完成」。
 *       这里放宽到与本项目其它外部调用一致的 5 秒。</li>
 * </ol>
 * 校验器与 claim 类型转换逐字沿用默认工厂的口径（时间戳 + OidcIdTokenValidator、默认 ClaimTypeConverter）。
 */
public class CachingOidcIdTokenDecoderFactory implements JwtDecoderFactory<ClientRegistration> {

    private static final Duration JWKS_TIMEOUT = Duration.ofSeconds(5);

    private final SignatureAlgorithm signatureAlgorithm;
    private final RestTemplate jwksRestTemplate;
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public CachingOidcIdTokenDecoderFactory(SignatureAlgorithm signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(JWKS_TIMEOUT);
        requestFactory.setReadTimeout(JWKS_TIMEOUT);
        this.jwksRestTemplate = new RestTemplate(requestFactory);
    }

    @Override
    public JwtDecoder createDecoder(ClientRegistration registration) {
        return decoders.computeIfAbsent(registration.getRegistrationId(), id -> buildDecoder(registration));
    }

    private JwtDecoder buildDecoder(ClientRegistration registration) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(registration.getProviderDetails().getJwkSetUri())
                .jwsAlgorithm(signatureAlgorithm)
                .restOperations(jwksRestTemplate)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new OidcIdTokenValidator(registration)));
        decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
        return decoder;
    }
}
