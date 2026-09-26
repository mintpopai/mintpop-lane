package ai.mintpop.lane.repository;

import ai.mintpop.lane.entity.LinkAlertState;

import java.util.Optional;

/** 链路告警去重状态（link_alert_state）的读写口。上层只依赖这个接口，看不到 MyBatis-Plus。 */
public interface LinkAlertStateRepository {

    /** 按唯一键 (userId, failureDomain, asn) 查找当前去重状态；不存在时返回空，由调用方决定默认值 */
    Optional<LinkAlertState> find(Long userId, String failureDomain, String asn);

    /**
     * 按唯一键 (user_id, failure_domain, asn) 幂等写入：命中则覆盖 alerted/alerted_at，不新增行。
     * {@code asn} 必须已由调用方把 null 转换成空串——本表的 asn 是 NOT NULL DEFAULT ''。
     */
    void upsert(LinkAlertState state);
}
