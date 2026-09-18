package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntryIpHistoryRepositoryTest extends MysqlTestBase {

    @Autowired
    private EntryIpHistoryRepository repository;

    private EntryIpHistory history(String failureDomain, DnsVantage vantage, String entryIps) {
        EntryIpHistory history = new EntryIpHistory();
        history.setFailureDomain(failureDomain);
        history.setVantage(vantage);
        history.setEntryIps(entryIps);
        return history;
    }

    @Test
    @DisplayName("findLatest 按 observed_at 取该故障域该视角的最新一条")
    void findsLatestByDomainAndVantage() {
        repository.create(history("jp.tsdns.top", DnsVantage.OVERSEAS, "1.1.1.1"));
        repository.create(history("jp.tsdns.top", DnsVantage.OVERSEAS, "2.2.2.2"));
        repository.create(history("jp.tsdns.top", DnsVantage.CHINA_TELECOM, "3.3.3.3"));

        assertThat(repository.findLatest("jp.tsdns.top", DnsVantage.OVERSEAS))
                .get().extracting(EntryIpHistory::getEntryIps).isEqualTo("2.2.2.2");
    }

    @Test
    @DisplayName("没有历史时返回 empty")
    void returnsEmptyWhenNoHistory() {
        assertThat(repository.findLatest("unknown.example.com", DnsVantage.OVERSEAS)).isEmpty();
    }

    @Test
    @DisplayName("asns 列能原样往返，观测时间由数据库默认值补齐")
    void createRoundTripsAsnsAndObservedAt() {
        EntryIpHistory record = history("jp.tsdns.top", DnsVantage.CHINA_UNICOM, "1.1.1.1,2.2.2.2");
        record.setAsns("AS1,AS2");

        repository.create(record);

        EntryIpHistory loaded = repository.findLatest("jp.tsdns.top", DnsVantage.CHINA_UNICOM).orElseThrow();
        assertThat(loaded.getAsns()).isEqualTo("AS1,AS2");
        assertThat(loaded.getObservedAt()).isNotNull();
    }

    @Test
    @DisplayName("应用显式塞进来的观测时间不会被写库——这一列的契约是「只由数据库 CURRENT_TIMESTAMP 维护」")
    void ignoresApplicationSuppliedObservedAt() {
        EntryIpHistory record = history("jp.tsdns.top", DnsVantage.CHINA_MOBILE, "1.1.1.1");
        record.setObservedAt(Instant.parse("2000-01-01T00:00:00Z"));

        repository.create(record);

        assertThat(repository.findLatest("jp.tsdns.top", DnsVantage.CHINA_MOBILE).orElseThrow().getObservedAt())
                .isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }
}
