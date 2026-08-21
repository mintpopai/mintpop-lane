package ai.mintpop.lane.dto;

import lombok.Data;
import lombok.ToString;

import java.time.Instant;

/** 分组的明文领域对象。订阅链接含 token，排除出 toString 防日志外泄。 */
@Data
public class NodeGroupDto {

    private Long id;

    private String name;

    /** 订阅链接明文 */
    @ToString.Exclude
    private String subUrl;

    private String remark;

    private Instant createdAt;

    private Instant updatedAt;
}
