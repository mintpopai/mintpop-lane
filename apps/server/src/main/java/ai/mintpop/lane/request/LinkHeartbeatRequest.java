package ai.mintpop.lane.request;

import lombok.Data;

import java.time.Instant;

/**
 * 心跳携带的链路上报块：客户端按 5 分钟窗口聚合的第一跳健康状况。
 * 整份请求体本身可空（老客户端 POST 不带 body），本类内部各字段的可空性见下方注释——
 * 这张表是冻结契约，桌面端 DTO（Task 8）逐字对齐，改这里必须先改契约文档。
 * <p>
 * 刻意不加 Bean Validation（{@code @NotNull} 等）：心跳承载的是「用户还能不能用」，
 * 上报数据格式有问题最多算丢一个观测窗口，不能因为 {@code @Valid} 校验失败
 * 就在进入方法体之前把整个心跳请求判成参数错误（见 {@code GlobalExceptionHandler}
 * 里 {@code MethodArgumentNotValidException} 的处理：那会让 {@code data} 整体变 null，
 * 客户端拿不到 {@code status} 字段）。校验与异常兜底统一收在
 * {@link ai.mintpop.lane.service.LinkReportService#ingest} 内部，格式不对就整条丢弃。
 */
@Data
public class LinkHeartbeatRequest {

    /** 故障域；客户端尚未解析出故障域时为 null */
    private String failureDomain;

    /** 上报窗口起点（UTC） */
    private Instant windowStart;

    /** 窗口内的采样聚合 */
    private Window window;

    /** alive 样本的延迟中位数（毫秒）；窗口内没有 alive 样本时为 null，0 是合法值 */
    private Integer p50LatencyMs;

    /** 窗口内该故障域对应 fallback 组 now 字段发生变化的次数 */
    private Integer failovers;

    /** 客户端实际解析到的中转入口 IP；客户端 DNS 解析失败时为 null */
    private String resolvedEntryIp;

    /** 窗口内的采样聚合：三个计数字段契约上都不可空 */
    @Data
    public static class Window {

        /** 窗口内的有效采样次数，成功率的分母 */
        private Integer samples;

        /** 有效采样里 alive 为真的次数，成功率的分子 */
        private Integer alive;

        /** history 为空、无法判定通断的次数；不计入 samples */
        private Integer noSample;
    }
}
