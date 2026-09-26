package ai.mintpop.lane.config;

import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四期新增的三个配置键（{@code ip-asn-cache.ttl}、{@code ip-asn-cache.max-entries}、
 * {@code link-report.alert-dedup-ttl}）此前只靶了默认值，删掉绑定也不会有测试变红。
 * 仿照 {@code application-test.yaml} 里 {@code lane.link.ttl-seconds} 的既有惯例：
 * 测试值特意与默认值不同，若把 {@code @ConfigurationProperties} 绑定删掉，字段会回落到
 * 默认值，下面的断言必须能因此变红。
 * <p>
 * {@code DnsConfig#ipAsnClient} 这条装配线是否真的把 {@link IpAsnCacheProperties} 的值
 * 用进了 {@code CachingIpAsnClient} 另见 {@code DnsConfigIpAsnClientWiringTest}
 * （本类只管「配置绑到了 Properties 对象上」这一半）。
 */
class LinkObservabilityConfigPropertiesTest extends MysqlTestBase {

    @Autowired
    private IpAsnCacheProperties ipAsnCacheProperties;

    @Autowired
    private LinkReportProperties linkReportProperties;

    @Test
    @DisplayName("ip-asn-cache.ttl / max-entries 绑到了非默认的测试值")
    void ipAsnCachePropertiesAreBound() {
        assertThat(ipAsnCacheProperties.getTtl()).isEqualTo(Duration.ofHours(2));
        assertThat(ipAsnCacheProperties.getMaxEntries()).isEqualTo(123);
    }

    @Test
    @DisplayName("link-report.alert-dedup-ttl 绑到了非默认的测试值")
    void alertDedupTtlIsBound() {
        assertThat(linkReportProperties.getAlertDedupTtl()).isEqualTo(Duration.ofHours(36));
    }
}
