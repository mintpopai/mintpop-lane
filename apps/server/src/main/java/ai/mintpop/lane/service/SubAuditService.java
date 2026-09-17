package ai.mintpop.lane.service;

import ai.mintpop.lane.response.SubAuditResponse;

/**
 * 订阅尽调：采购候选机场前的只读决策接口。给一个试用订阅链接，判断它的节点是否与
 * 库里已有节点撞故障域——撞了说明两家机场实际共用同一家中转入口，冗余是假的。
 */
public interface SubAuditService {

    /** @param subUrl 候选机场的订阅链接 */
    SubAuditResponse audit(String subUrl);
}
