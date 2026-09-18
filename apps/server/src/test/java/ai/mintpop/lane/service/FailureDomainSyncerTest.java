package ai.mintpop.lane.service;

import ai.mintpop.lane.client.FailureDomainResolver;
import ai.mintpop.lane.dto.ProxyNodeDto;
import ai.mintpop.lane.parser.SubNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 故障域解析与写入的共用口径：导入、定时刷新、采购尽调三处共用它，
 * 「按 serverAddr 去重」「跳过伪条目」「解析失败保留原值」这三条改坏一条就是三处一起坏。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("故障域同步器：按域名去重、跳过伪条目、解析失败不抹旧值")
class FailureDomainSyncerTest {

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Mock
    private FailureDomainResolver resolver;

    private FailureDomainSyncer syncer;

    @BeforeEach
    void setUp() {
        syncer = new FailureDomainSyncer(resolver, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private SubNode node(String name, String serverAddr, boolean suspectedInfo) {
        return new SubNode(name, "anytls", serverAddr, 35660, Map.of(), suspectedInfo);
    }

    @Test
    @DisplayName("同一 serverAddr 的多个节点只查一次 DNS")
    void resolvesEachServerAddrOnce() {
        when(resolver.resolve("hk01a.example.com")).thenReturn("hk.tsdns.top");

        Map<String, String> domains = syncer.resolve(List.of(
                node("US-01", "hk01a.example.com", false),
                node("US-02", "hk01a.example.com", false),
                node("US-03", "hk01a.example.com", false)));

        verify(resolver, times(1)).resolve("hk01a.example.com");
        assertThat(domains).containsExactly(entry("hk01a.example.com", "hk.tsdns.top"));
    }

    @Test
    @DisplayName("伪条目（剩余流量 / 到期时间这类）一次 DNS 都不查")
    void skipsSuspectedInfoEntries() {
        when(resolver.resolve(anyString())).thenReturn("hk.tsdns.top");

        Map<String, String> domains = syncer.resolve(List.of(
                node("剩余流量：18.2 GB", "info.example.com", true),
                node("US-01", "hk01a.example.com", false)));

        verify(resolver, never()).resolve("info.example.com");
        assertThat(domains).containsOnlyKeys("hk01a.example.com");
    }

    @Test
    @DisplayName("解析失败的 serverAddr 不进表，调用方据此保留节点原值")
    void omitsUnresolvedServerAddrs() {
        when(resolver.resolve("hk01a.example.com")).thenReturn(null);

        assertThat(syncer.resolve(List.of(node("US-01", "hk01a.example.com", false)))).isEmpty();
    }

    @Test
    @DisplayName("写入故障域时一并记下解析时间，取注入的 Clock")
    void appliesDomainAndCheckedAt() {
        ProxyNodeDto node = new ProxyNodeDto();

        syncer.apply(node, "hk01a.example.com", Map.of("hk01a.example.com", "hk.tsdns.top"));

        assertThat(node.getFailureDomain()).isEqualTo("hk.tsdns.top");
        assertThat(node.getFailureDomainCheckedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("表里没有该 serverAddr 时保留原值不动——网络抖动不代表拓扑变了")
    void keepsPreviousValueWhenUnresolved() {
        ProxyNodeDto node = new ProxyNodeDto();
        node.setFailureDomain("jp.tsdns.top");
        node.setFailureDomainCheckedAt(Instant.parse("2026-09-01T00:00:00Z"));

        syncer.apply(node, "hk01a.example.com", Map.of());

        assertThat(node.getFailureDomain()).isEqualTo("jp.tsdns.top");
        assertThat(node.getFailureDomainCheckedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }
}
