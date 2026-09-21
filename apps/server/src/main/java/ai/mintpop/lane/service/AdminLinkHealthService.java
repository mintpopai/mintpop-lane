package ai.mintpop.lane.service;

import ai.mintpop.lane.response.LinkHealthResponse;

/** 管理端链路健康查询：故障域 × 运营商成功率矩阵 + 入口 IP 变更时间线（spec §8.3） */
public interface AdminLinkHealthService {

    /**
     * @param requestedDays 调用方想回看的天数，未必会被原样采用——超出 [1, 按天聚合保留天数]
     *                       会被收敛到边界，不接受调用方传入的原始值直接拖库查询
     */
    LinkHealthResponse getLinkHealth(int requestedDays);
}
