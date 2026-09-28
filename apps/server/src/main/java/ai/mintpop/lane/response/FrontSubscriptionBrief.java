package ai.mintpop.lane.response;

/** 用户第一跳列表的一项，管理端按顺位展示 */
public record FrontSubscriptionBrief(
        /** 顺位：0 主用，1、2 备用 */
        int position,
        Long airportSubscriptionId,
        String airportName,
        String subscriptionName,
        String account
) {
}
