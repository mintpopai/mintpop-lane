package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户的入参。用户由登录自动建档，这里只管管理员能动的部分：
 * 展示名、处置态、链路资源分配。subject/email 随身份走，接口上不可改；
 * role 刻意不含：授予/撤销管理员一律改库，接口上开这个口子等于给自己留提权后门。
 */
@Data
public class UserSaveRequest {

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名最长 64 个字符")
    private String name;

    @NotNull(message = "状态不能为空")
    private UserStatus status;

    /** 第一跳节点 id，null 表示不分配 */
    private Long frontNodeId;

    /** 落地节点 id，null 表示不分配 */
    private Long landNodeId;
}
