package ai.mintpop.lane.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("故障域解析：取 CNAME 链的终点")
class FailureDomainResolverTest {

    /** 用一张 host -> cname 的表模拟 DNS；表里没有即无 CNAME 记录 */
    private FailureDomainResolver resolverOf(Map<String, String> chain) {
        return new JndiFailureDomainResolver(chain::get);
    }

    @Test
    @DisplayName("单级 CNAME 取到目标域名")
    void resolvesSingleCname() {
        FailureDomainResolver resolver = resolverOf(Map.of("hk01a.t11-a.app", "hk.tsdns.top"));
        assertThat(resolver.resolve("hk01a.t11-a.app")).isEqualTo("hk.tsdns.top");
    }

    @Test
    @DisplayName("多级 CNAME 一路跟到终点")
    void followsCnameChain() {
        FailureDomainResolver resolver = resolverOf(Map.of(
                "a.example.com", "b.example.com",
                "b.example.com", "c.example.com"));
        assertThat(resolver.resolve("a.example.com")).isEqualTo("c.example.com");
    }

    @Test
    @DisplayName("无 CNAME 时该节点自成一个故障域，返回它自己")
    void returnsHostWhenNoCname() {
        FailureDomainResolver resolver = resolverOf(Map.of());
        assertThat(resolver.resolve("hw01v.t11-a.app")).isEqualTo("hw01v.t11-a.app");
    }

    @Test
    @DisplayName("解析抛异常时返回 null，由调用方按未知处理、不写库")
    void returnsNullOnLookupFailure() {
        FailureDomainResolver resolver = new JndiFailureDomainResolver(host -> {
            throw new IllegalStateException("DNS 不通");
        });
        assertThat(resolver.resolve("whatever.example.com")).isNull();
    }

    @Test
    @DisplayName("CNAME 成环时中止并返回 null，不死循环")
    void returnsNullOnCycle() {
        FailureDomainResolver resolver = resolverOf(Map.of(
                "a.example.com", "b.example.com",
                "b.example.com", "a.example.com"));
        assertThat(resolver.resolve("a.example.com")).isNull();
    }
}
