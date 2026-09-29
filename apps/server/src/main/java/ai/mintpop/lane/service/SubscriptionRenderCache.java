package ai.mintpop.lane.service;

import java.util.List;
import java.util.Map;

/**
 * 订阅级渲染缓存：一个机场订阅当前应下发的那一组（故障域 + 已叠保活覆盖的节点 map）。
 * 心跳每 60 秒要为每个用户现算 configVersion，1 万用户约每秒 170 次；没有这层缓存就是每秒几百次节点表查询。
 * 做成接口是给将来换 Redis 实现留局部改动点（多实例部署时进程内缓存会让心跳与下发算出不同哈希）。
 */
public interface SubscriptionRenderCache {

    /** 订阅下没有节点时 nodes 为空列表、failureDomain 为 null；订阅不存在同样返回空 */
    RenderedSubscription get(Long airportSubscriptionId);

    /** 该订阅的节点被改动后调用（对齐、删除） */
    void evict(Long airportSubscriptionId);

    /** 影响所有订阅渲染的东西变了（全局配置的地区）时调用 */
    void evictAll();

    record RenderedSubscription(String failureDomain, List<Map<String, Object>> nodes) {
    }
}
