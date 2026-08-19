package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建或更新用户的入参。
 * 刻意不含 role：授予/撤销管理员一律改库，接口上开这个口子等于给自己留提权后门。
 */
@Data
public class UserSaveRequest {

    @NotBlank(message = "Logto user id 不能为空")
    @Size(max = 128, message = "Logto user id 最长 128 个字符")
    private String subject;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱最长 255 个字符")
    private String email;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 64, message = "姓名最长 64 个字符")
    private String name;

    @NotNull(message = "状态不能为空")
    private UserStatus status = UserStatus.ACTIVE;

    @NotNull(message = "第一跳节点不能为空")
    private Long frontNodeId;

    /** 落地节点 id，null 表示不分配 */
    private Long landNodeId;
}
