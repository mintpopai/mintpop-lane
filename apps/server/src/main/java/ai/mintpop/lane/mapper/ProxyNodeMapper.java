package ai.mintpop.lane.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ai.mintpop.lane.entity.ProxyNode;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 节点表的 SQL 层。CRUD 由 BaseMapper 提供，不写 XML。 */
public interface ProxyNodeMapper extends BaseMapper<ProxyNode> {

    /** 库里出现过的全部故障域，去重；NULL 不算。供入口 IP 巡检取要监测的域名集合 */
    @Select("SELECT DISTINCT failure_domain FROM proxy_node WHERE failure_domain IS NOT NULL")
    List<String> selectDistinctFailureDomains();
}
