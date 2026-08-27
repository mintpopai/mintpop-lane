package ai.mintpop.lane.client;

import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.enumeration.BizCodeEnum;
import ai.mintpop.lane.exception.BizException;

/**
 * 经落地出口探测公网 IP，并与节点登记的 egress_ip 比对。
 *
 * 失败必须分流：探测不通是网络问题（可重试），IP 不符是配置错误（要人工核对）。
 * 混成一句报错会把网络抖动误报成配置错误。
 *
 * 注意：本类刻意不加 @Component —— 它的构造依赖一个 EgressProbe，
 * 由配置类里的 @Bean 工厂方法装配。加了组件扫描会与 @Bean 重复注册。
 */
public class EgressIpVerifier {

    /** 出口探测的实现，抽成接口便于测试替换 */
    @FunctionalInterface
    public interface EgressProbe {
        String currentEgressIp(ProxyNodeDto land) throws Exception;
    }

    private static final String IP_ECHO_URL = "https://api.ipify.org";

    private final EgressProbe probe;

    public EgressIpVerifier(EgressProbe probe) {
        this.probe = probe;
    }

    /** 生产用构造：经落地代理请求 IP 回显服务 */
    public static EgressIpVerifier over(LandProxyClientFactory factory) {
        return new EgressIpVerifier(land -> {
            String body = factory.create(land)
                    .get()
                    .uri(IP_ECHO_URL)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("IP 回显服务返回空响应");
            }
            return body.trim();
        });
    }

    public void verify(ProxyNodeDto land) {
        String actual;
        try {
            actual = probe.currentEgressIp(land);
        } catch (Exception e) {
            throw new BizException(BizCodeEnum.EGRESS_PROBE_FAILED);
        }
        if (!land.getEgressIp().equals(actual)) {
            throw new BizException(BizCodeEnum.EGRESS_IP_MISMATCH);
        }
    }
}
