package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.LinkAlertState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/** link_alert_state 表的 SQL 层。查询由 BaseMapper 提供，写入需要 ON DUPLICATE KEY UPDATE，单写一条。 */
@Mapper
public interface LinkAlertStateMapper extends BaseMapper<LinkAlertState> {

    /**
     * 按唯一键 (user_id, failure_domain, isp) 幂等写入：命中则覆盖 alerted 与 alerted_at。
     * 从已告警变回未告警（恢复）同样走这条，落到同一行，不新增行。
     */
    @Insert("""
            INSERT INTO link_alert_state (user_id, failure_domain, isp, alerted, alerted_at)
            VALUES (#{userId}, #{failureDomain}, #{isp}, #{alerted}, #{alertedAt})
            ON DUPLICATE KEY UPDATE
                alerted = VALUES(alerted),
                alerted_at = VALUES(alerted_at)
            """)
    int upsert(LinkAlertState state);
}
