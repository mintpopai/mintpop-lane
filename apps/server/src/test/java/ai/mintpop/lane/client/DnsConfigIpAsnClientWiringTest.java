package ai.mintpop.lane.client;

import ai.mintpop.lane.config.IpAsnCacheProperties;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code DnsConfig#ipAsnClient} 这条装配线本身没有任何测试经过——{@link IpAsnCacheProperties}
 * 绑定生效（见 {@code LinkObservabilityConfigPropertiesTest}）不代表它真被用来构造
 * {@link CachingIpAsnClient}：若哪天把 {@code props.getTtl()}/{@code props.getMaxEntries()}
 * 误写成常量或 {@code CachingIpAsnClient} 自带的默认值，不会有测试变红。
 * <p>
 * {@code application-test.yaml} 把 {@code ip-asn-cache.ttl}/{@code max-entries} 特意设成
 * 与 {@link CachingIpAsnClient} 的默认值（24h / 5000）不同的测试值，本类直接读装配出来的
 * bean 的内部字段（包内可见 getter，仅供测试）断言它们确实来自配置而不是默认值。
 */
class DnsConfigIpAsnClientWiringTest extends MysqlTestBase {

    @Autowired
    private IpAsnClient ipAsnClient;

    @Autowired
    private IpAsnCacheProperties properties;

    @Test
    @DisplayName("DnsConfig 装出的 IpAsnClient 是 CachingIpAsnClient，且 ttl/maxEntries 来自 IpAsnCacheProperties")
    void ipAsnClientIsWiredWithConfiguredTtlAndCapacity() {
        assertThat(ipAsnClient).isInstanceOf(CachingIpAsnClient.class);
        CachingIpAsnClient caching = (CachingIpAsnClient) ipAsnClient;

        assertThat(caching.getTtl()).isEqualTo(properties.getTtl());
        assertThat(caching.getMaxEntries()).isEqualTo(properties.getMaxEntries());
    }
}
