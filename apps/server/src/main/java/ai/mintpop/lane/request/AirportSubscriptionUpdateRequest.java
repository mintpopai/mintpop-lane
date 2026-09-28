package ai.mintpop.lane.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑机场订阅的入参。只开放名称、账号、备注：所属机场与带宽创建后不可改——
 * 改机场会破坏「每个用户的列表里每家机场最多一次」，改带宽会让已分配的主用人数与容量对不上。
 * 换链接等于建新订阅。
 */
@Data
public class AirportSubscriptionUpdateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    @Size(max = 128)
    private String account;

    @Size(max = 255)
    private String remark;
}
