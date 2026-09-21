package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.EntryIpHistory;
import ai.mintpop.lane.enumeration.DnsVantage;
import ai.mintpop.lane.support.MysqlTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

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

    @Test
    @DisplayName("findAllOrderByDomainVantageAndTime 按故障域、视角分组，组内按观测时间升序")
    void findAllOrdersByDomainThenVantageThenTime() {
        // 本类没有每用例清库，用本测试独占的故障域名避免与其它用例的数据互相干扰
        String domain = "kr.tsdns.top";
        // 故意乱序写入，断言读出来的顺序与写入顺序无关，只与分组+时间有关
        repository.create(history(domain, DnsVantage.OVERSEAS, "2.2.2.2"));
        repository.create(history(domain, DnsVantage.CHINA_TELECOM, "9.9.9.9"));
        repository.create(history(domain, DnsVantage.OVERSEAS, "1.1.1.1"));

        List<EntryIpHistory> all = repository.findAllOrderByDomainVantageAndTime();

        List<EntryIpHistory> overseas = all.stream()
                .filter(h -> h.getVantage() == DnsVantage.OVERSEAS && h.getFailureDomain().equals(domain))
                .toList();
        assertThat(overseas).hasSize(2);
        // 先写入的 "2.2.2.2" observed_at 更早，即使后写入的 "1.1.1.1" 在集合里物理顺序更靠后，
        // 按观测时间升序排出来仍然是 "2.2.2.2" 在前
        assertThat(overseas.get(0).getEntryIps()).isEqualTo("2.2.2.2");
        assertThat(overseas.get(1).getEntryIps()).isEqualTo("1.1.1.1");
    }
}
