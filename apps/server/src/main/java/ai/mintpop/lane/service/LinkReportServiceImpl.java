package ai.mintpop.lane.service;

import ai.mintpop.lane.client.IpAsnClient;
import ai.mintpop.lane.entity.LinkReport;
import ai.mintpop.lane.repository.LinkReportRepository;
import ai.mintpop.lane.request.LinkHeartbeatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LinkReportServiceImpl implements LinkReportService {

    private final LinkReportRepository linkReportRepository;
    private final IpAsnClient ipAsnClient;

    public LinkReportServiceImpl(LinkReportRepository linkReportRepository, IpAsnClient ipAsnClient) {
        this.linkReportRepository = linkReportRepository;
        this.ipAsnClient = ipAsnClient;
    }

    @Override
    public void ingest(Long userId, LinkHeartbeatRequest request, String sourceIp) {
        // 整个上报处理绝不外抛：心跳承载的是「用户还能不能用」，观测数据丢一个窗口无所谓，
        // 把心跳搞挂会让客户端误判成链路失效、当场断链。格式不对（如老/坏客户端漏填
        // 契约里标注为不可空的字段）在这里会以 NPE/落库异常的形式出现，同样被吞掉，
        // 只留日志，不整条重试、也不让心跳感知到。
        try {
            LinkReport report = toEntity(userId, request);
            // ASN 反查用注入的 IpAsnClient；查不到时 report.sourceAsn 保持 null。
            // isp 全程不填：IpAsnClient 只返回 ASN 字符串，没有运营商名可填，
            // ASN → 运营商名的映射留给 Task 5 展示时做；null 是这一列既定的
            // 「暂无数据」编码（与 LinkReportRepository#aggregateByDomainAndIsp 的
            // DomainIspAggregate.isp() 文档同一语义），不能改存空串——那会与
            // failureDomain 的空串编码混淆，把「没查到」误判成「查到了空运营商」
            ipAsnClient.lookupAsn(sourceIp).ifPresent(report::setSourceAsn);
            linkReportRepository.upsertWindow(report);
        } catch (Exception e) {
            log.warn("链路上报落库失败，本窗口丢弃，userId={}", userId, e);
        }
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
