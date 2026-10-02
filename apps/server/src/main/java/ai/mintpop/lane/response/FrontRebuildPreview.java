package ai.mintpop.lane.response;

/**
 * 全体重算的容量预检：需要 = 要重排的激活席位用户数，现有 = 有节点的订阅按每人带宽算出的主用名额之和。
 * 保留手动分配时，需要里不含手动用户，现有里先扣掉他们占的名额；keptManualCount 为保留的手动用户数（覆盖时为 0）
 */
public record FrontRebuildPreview(int requiredPrimary, int availablePrimary, boolean sufficient, int keptManualCount) {
}
