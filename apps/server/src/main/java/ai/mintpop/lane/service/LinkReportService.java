package ai.mintpop.lane.service;

import ai.mintpop.lane.request.LinkHeartbeatRequest;

/** 心跳携带的链路上报块的落库口。 */
public interface LinkReportService {

    /**
     * 接住一个上报窗口：把契约层字段映射到存储层编码，反查来源 IP 的 ASN 后落库。
     * 整个过程绝不向外抛异常——心跳承载的是「用户还能不能用」，观测数据丢一个窗口
     * 无所谓，把心跳搞挂会让客户端误判成链路失效、当场断链。
     *
     * @param userId   上报用户
     * @param request  上报块；调用方保证非 null（是否携带上报块由 Controller 判断）
     * @param sourceIp 请求的来源 IP，用于反查 ASN
     */
    void ingest(Long userId, LinkHeartbeatRequest request, String sourceIp);
}
