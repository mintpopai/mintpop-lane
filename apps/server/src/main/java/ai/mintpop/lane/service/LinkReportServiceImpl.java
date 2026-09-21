package ai.mintpop.lane.service;

import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.request.LinkHeartbeatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
public class LinkReportServiceImpl implements LinkReportService {

    private final LinkReportRepository linkReportRepository;
    private final IpAsnClient ipAsnClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LinkReportProperties properties;

    public LinkReportServiceImpl(LinkReportRepository linkReportRepository, IpAsnClient ipAsnClient,
                                  ObjectMapper objectMapper, Clock clock, LinkReportProperties properties) {
        this.linkReportRepository = linkReportRepository;
        this.ipAsnClient = ipAsnClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public void ingest(Long userId, String rawReport, String sourceIp) {
        // 整个上报处理（含 JSON 解析）绝不外抛：心跳承载的是「用户还能不能用」，观测数据
        // 丢一个窗口无所谓，把心跳搞挂会让客户端误判成链路失效、当场断链。JSON 语法错误、
        // 字段类型不匹配（Jackson 3 的 JacksonException 是 RuntimeException）、契约里标注
        // 为不可空的字段实际缺失（表现为下面的 NPE）、落库异常，全部在这一个 try 里被吞掉，
        // 只留日志，不整条重试、也不让心跳感知到。
        try {
            LinkHeartbeatRequest request = objectMapper.readValue(rawReport, LinkHeartbeatRequest.class);

            if (!windowStartWithinTolerance(request.getWindowStart())) {
                log.warn("上报窗口起点超出容忍范围，丢弃：userId={} windowStart={}", userId, request.getWindowStart());
                return;
            }

            LinkReport report = toEntity(userId, request);

            if (!countsAreSane(report)) {
                log.warn("上报计数不合理，丢弃：userId={} samples={} alive={} noSample={} failovers={} p50={}",
                        userId, report.getSamples(), report.getAliveCount(), report.getNoSampleCount(),
                        report.getFailovers(), report.getP50LatencyMs());
                return;
            }

            // 运营商由服务端按来源 IP 反查，不让客户端自报（spec §8.1：自报不可信，客户端也不知道）。
            //
            // 反查刻意保持**同步**跑在心跳请求线程上，不改成 @Async：异步化会让「POST 完立刻
            // 断言落库」的一整批测试全部改形（要么加等待、要么改成轮询），换来的收益有限。
            // 代价与上界说清楚：ipAsnClient 外面套了 CachingIpAsnClient（按 IP、TTL 24 小时），
            // 于是**只有某个来源 IP 在缓存里的第一次**会真打一次 HTTP，最坏情况慢一次反查超时
            // （GeoIpConfig：connect 5s + read 5s）；此后 24 小时内同一 IP 都是内存命中。
            // 桌面端心跳的客户端超时是 15s，单次反查超时仍在其内，但这是本路径最接近红线的地方——
            // 哪天要再往心跳里加外部调用，先回来看这段。
            //
            // 一次反查同时得到 asn 与运营商名：asn 落 source_asn，isp 优先用可读的运营商名，
            // 上游没给（AsnInfo.isp() 为 null）时退回 ASN 串（如 "AS4134"）。退回而不是留 null
            // 是要害——isp 为 null 的样本会被 LinkReportAlertService 跳过运营商级判定、并在
            // 管理端矩阵里归进「未知运营商」那一行，spec §8.3 的「单运营商成功率异常」就永远不触发。
            //
            // 反查整体失败（Optional.empty）时 source_asn 与 isp 都保持 null：null 是本表
            // 这两列既定的「暂无数据」编码（与 LinkReportRepository#aggregateByDomainAndIsp 的
            // DomainIspAggregate.isp() 文档同一语义），不能改存空串——空串是 link_report_daily
            // 那张表（NOT NULL DEFAULT ''）的编码，混用会把「没查到」误判成「查到了空运营商」
            ipAsnClient.lookup(sourceIp).ifPresent(info -> {
                report.setSourceAsn(info.asn());
                report.setIsp(info.isp() == null ? info.asn() : info.isp());
            });
            linkReportRepository.upsertWindow(report);
        } catch (Exception e) {
            log.warn("链路上报处理失败，本窗口丢弃，userId={}", userId, e);
        }
    }

    /**
     * 窗口起点必须落在 [现在 - windowMaxPast, 现在 + windowMaxFuture] 内，否则丢弃：
     * 客户端时钟不准、或请求被构造出任意时间时，过旧的窗口会污染按天聚合的口径，
     * 过新（未来）的窗口会让 findWindowsBefore 永远扫不到它、归档任务永远清不掉。
     * request.getWindowStart() 为 null（契约里这个字段不可空，缺失属于格式错误）
     * 时同样判定为越界，交给外层 try/catch 统一吞掉。
     */
    private boolean windowStartWithinTolerance(Instant windowStart) {
        if (windowStart == null) {
            return false;
        }
        Instant now = clock.instant();
        Instant earliest = now.minus(properties.getWindowMaxPast());
        Instant latest = now.plus(properties.getWindowMaxFuture());
        return !windowStart.isBefore(earliest) && !windowStart.isAfter(latest);
    }

    /**
     * 计数的合理性校验：不满足就整块丢弃（与格式错误、窗口越界同一处置）。
     * <p>
     * spec §8.1 之所以让运营商由服务端反查而不让客户端自报，理由是「自报不可信」；
     * 计数同样是客户端自报的，同一条理由适用。客户端 bug 或手工构造的请求能写进：
     * <ul>
     *   <li>{@code alive > samples} → 成功率 >100%，把这个故障域的真实劣化掩盖掉，
     *       并且带着这个比值进全库矩阵；</li>
     *   <li>负 {@code samples} → {@link LinkReportAlertService} 的 {@code samples <= 0}
     *       门槛会把整段判定直接跳过，等于给了一条「让告警闭嘴」的路；</li>
     *   <li>负延迟 → 没有物理意义，只会污染展示。</li>
     * </ul>
     * 必填计数为 null 也判为不合理：这几列在 {@code link_report} 都是 NOT NULL，
     * 放过去只是把一个清楚的校验失败换成一条数据库异常，日志还更难读。
     * <p>
     * {@code p50LatencyMs} 是唯一允许为 null 的（窗口内没有 alive 样本），
     * 但为 0 是**合法**的低延迟而不是失败（spec §8.1 陷阱二），所以判的是 {@code < 0}。
     * 全 0 的窗口（整段离线）同样合法，不许被这道校验误杀。
     */
    private boolean countsAreSane(LinkReport report) {
        Integer samples = report.getSamples();
        Integer alive = report.getAliveCount();
        Integer noSample = report.getNoSampleCount();
        Integer failovers = report.getFailovers();
        Integer p50 = report.getP50LatencyMs();
        if (samples == null || alive == null || noSample == null || failovers == null) {
            return false;
        }
        return samples >= 0 && alive >= 0 && alive <= samples && noSample >= 0 && failovers >= 0
                && (p50 == null || p50 >= 0);
    }

    /** 契约层 → 存储层的字段映射；failureDomain 的 null ⇄ 空串转换只发生在这一处 */
    private LinkReport toEntity(Long userId, LinkHeartbeatRequest request) {
        LinkReport report = new LinkReport();
        report.setUserId(userId);
        report.setFailureDomain(request.getFailureDomain() == null ? "" : request.getFailureDomain());
        report.setWindowStart(request.getWindowStart());
        LinkHeartbeatRequest.Window window = request.getWindow();
        report.setSamples(window.getSamples());
        report.setAliveCount(window.getAlive());
        report.setNoSampleCount(window.getNoSample());
        report.setP50LatencyMs(request.getP50LatencyMs());
        report.setFailovers(request.getFailovers());
        report.setResolvedEntryIp(request.getResolvedEntryIp());
        return report;
    }
}
