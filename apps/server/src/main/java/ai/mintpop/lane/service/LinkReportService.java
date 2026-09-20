package ai.mintpop.lane.service;

/** 心跳携带的链路上报块的落库口。 */
public interface LinkReportService {

    /**
     * 接住一个上报窗口：解析原始 JSON、把契约层字段映射到存储层编码、校验窗口时间范围，
     * 反查来源 IP 的 ASN 后落库。整个过程（含 JSON 解析）绝不向外抛异常——心跳承载的是
     * 「用户还能不能用」，观测数据丢一个窗口无所谓，把心跳搞挂会让客户端误判成链路失效、
     * 当场断链。
     * <p>
     * 刻意接收<b>原始 JSON 文本</b>而不是已解析好的请求对象：如果 Controller 用
     * {@code @RequestBody LinkHeartbeatRequest} 这种形参，Spring 会在方法体执行<b>之前</b>
     * 做反序列化，语法错误/字段类型不匹配会被 {@code GlobalExceptionHandler} 的
     * {@code HttpMessageNotReadableException} 分支接住，让整条心跳的 {@code data} 变成
     * {@code null}——这与本方法「异常不外抛」的设计完全绕开，客户端拿不到
     * {@code HeartbeatResponse} 会误判链路失效并断链。把解析挪到这里、包进同一个
     * try/catch，才能保证任何格式问题都只丢一个窗口。
     *
     * @param userId    上报用户
     * @param rawReport 上报块的原始 JSON 文本；调用方保证非空/非 null
     *                  （是否携带上报块由 Controller 判断）
     * @param sourceIp  请求的来源 IP，用于反查 ASN
     */
    void ingest(Long userId, String rawReport, String sourceIp);
}
