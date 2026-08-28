package ai.mintpop.lane.client;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.NodeProtocol;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.Objects;

/**
 * 按落地节点构造「经该出口出站」的 RestClient。
 *
 * 每次调用新建实例、不缓存不复用：不同席位的落地节点凭据不同，代理配置绑在实例上。
 *
 * ⚠️ 底层必须是 Reactor Netty，不能换成 JDK 的 java.net.http.HttpClient——
 * 后者的 SOCKS5 用户名密码认证由 java.net.SocksSocketImpl 处理，硬编码调用
 * 全局 java.net.Authenticator.setDefault()，多席位并发签发会互相串认证。
 * Reactor Netty 自己实现 SOCKS5 握手（Netty 的 Socks5ProxyHandler），
 * 凭据绑在 client 实例上，且同一套代码只换枚举值就能覆盖 HTTP 代理。
 */
@Component
public class LandProxyClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

    public RestClient create(ProxyNodeDto land) {
        Objects.requireNonNull(land, "落地节点不能为空");
        String username = stringValue(land, "username");
        String password = stringValue(land, "password");

        HttpClient nettyClient = HttpClient.create()
                .responseTimeout(RESPONSE_TIMEOUT)
                .proxy(spec -> {
                    ProxyProvider.Builder builder = spec
                            .type(proxyTypeOf(land.getProtocol()))
                            .host(land.getServerAddr())
                            .port(land.getPort())
                            .connectTimeoutMillis(CONNECT_TIMEOUT.toMillis());
                    // 无凭据的代理不设用户名，否则 Netty 会强制走认证子协商
                    if (!username.isEmpty()) {
                        builder.username(username).password(user -> password);
                    }
                });

        return RestClient.builder()
                .requestFactory(new ReactorClientHttpRequestFactory(nettyClient))
                .build();
    }

    /** 节点协议到 Reactor Netty 代理类型的映射；只有落地节点放行的两种会走到这里 */
    private ProxyProvider.Proxy proxyTypeOf(NodeProtocol protocol) {
        return switch (protocol) {
            case SOCKS5 -> ProxyProvider.Proxy.SOCKS5;
            case HTTP -> ProxyProvider.Proxy.HTTP;
            default -> throw new IllegalArgumentException("落地节点不支持的协议：" + protocol);
        };
    }

    private String stringValue(ProxyNodeDto land, String key) {
        Object value = land.getSecret() == null ? null : land.getSecret().get(key);
        return value == null ? "" : value.toString();
    }
}
