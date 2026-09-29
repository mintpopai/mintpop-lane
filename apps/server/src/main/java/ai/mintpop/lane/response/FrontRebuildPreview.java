package ai.mintpop.lane.response;

/** 全体重算的容量预检：需要 = 激活席位用户数，现有 = 有节点的订阅按每人带宽算出的主用名额之和 */
public record FrontRebuildPreview(int requiredPrimary, int availablePrimary, boolean sufficient) {
}
