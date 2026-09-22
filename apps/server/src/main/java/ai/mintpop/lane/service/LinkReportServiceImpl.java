package ai.mintpop.lane.service;

import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.client.IpAsnClient.AsnInfo;
import ai.mintpop.lane.config.LinkReportProperties;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.repository.AsnOrgRepository;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.request.LinkHeartbeatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class LinkReportServiceImpl implements LinkReportService {

    private final LinkReportRepository linkReportRepository;
    private final IpAsnClient ipAsnClient;
    private final AsnOrgRepository asnOrgRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final LinkReportProperties properties;

    public LinkReportServiceImpl(LinkReportRepository linkReportRepository, IpAsnClient ipAsnClient,
                                  AsnOrgRepository asnOrgRepository, ObjectMapper objectMapper, Clock clock,
                                  LinkReportProperties properties) {
        this.linkReportRepository = linkReportRepository;
        this.ipAsnClient = ipAsnClient;
        this.asnOrgRepository = asnOrgRepository;
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
            // 运营商维度以 ASN 做键，本表只落 source_asn：展示名是上游给的自由文本、随时漂移，
            // 拿它做键会把同一家运营商裂成两列，故名字不进这张窗口表——它另按 ASN 存进 asn_org，
            // 由下面的 recordOrgName 在窗口落库之后单独写（旁路，失败不牵连本次上报）。
            //
            // 反查整体失败（Optional.empty）时 source_asn 保持 null：null 是本列既定的
            // 「暂无数据」编码（与 LinkReportRepository#aggregateByDomainAndAsn 的
            // DomainAsnAggregate 文档同一语义），不能改存空串——空串是 link_report_daily
            // 那张表（NOT NULL DEFAULT ''）的编码，混用会把「没查到」误判成「查到了空 ASN」
            Optional<AsnInfo> asnInfo = ipAsnClient.lookup(sourceIp);
            asnInfo.ifPresent(info -> report.setSourceAsn(info.asn()));

            // 主写入在前：本方法的全部价值就是这一行进库
            linkReportRepository.upsertWindow(report);
            // 展示名是旁路，排在主写入之后、并且自己兜住异常，见 recordOrgName
            asnInfo.ifPresent(this::recordOrgName);
        } catch (Exception e) {
            log.warn("链路上报处理失败，本窗口丢弃，userId={}", userId, e);
        }
    }

    /**
     * 把这个 ASN 的展示名记进 {@code asn_org}（首次见到时记一次，此后不覆盖）——这里是这张映射表
     * 在生产里唯一的写入来源，不记则告警文案与管理端矩阵只能显示一串 AS 号，没人看得出是哪家运营商。
     * <p>
     * 刻意排在 {@code upsertWindow} <b>之后</b>、且<b>自己吞掉全部异常</b>，不与上报窗口共用外层
     * 那个 try：{@code asn_org} 存的是纯展示数据（丢了只是文案退回 AS 号），{@code link_report}
     * 才是这一期的全部价值，两者的失败不该被绑在一起。若让它排在前面又共用同一个 catch，
     * {@code insertIfAbsent} 一抛异常就会让 {@code upsertWindow} 根本执行不到——而迁移没跑到、
     * 表权限不对、死锁这类故障是<b>持续性</b>的，于是所有反查成功的上报都会长期静默停摆，
     * 心跳却照样返回 200，没有任何地方看得出来。
     * <p>
     * 上游没给名字（null）就不写：{@code org_name} 是 NOT NULL，写空串等于把这个 ASN 的展示名
     * 永久钉成空（{@code asn_org} 是「有则不动」，写下去就改不掉了）。
     */
    private void recordOrgName(AsnInfo info) {
        if (info.isp() == null) {
            return;
        }
        try {
            // 展示名只记首次见到的那个：它的用处是让人认得出，稳定比新鲜重要——
            // 上游同一家运营商今天叫 China Telecom、明天叫 CHINANET-BACKBONE，
            // 跟着漂会让同一个 ASN 的历史裂成两段（不覆盖由 insertIfAbsent 自己保证）
            asnOrgRepository.insertIfAbsent(info.asn(), IpAsnClient.truncateIsp(info.isp()), clock.instant());
        } catch (Exception e) {
            log.warn("记录 ASN 展示名失败，本次上报窗口已落库、不受影响，asn={}", info.asn(), e);
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
