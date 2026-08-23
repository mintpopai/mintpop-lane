package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新用户的入参。用户由登录自动建档，这里只管管理员能动的部分：
 * 处置态、链路资源分配。subject/email 随身份走，接口上不可改——
 * email 刻意不开放：它是用户的唯一业务标识，且登录同步会用 Logto 的 email 覆盖库里的值，
 * 管理员若能改，下次登录就会被静默回滚，只会制造「改了但又变回去了」的困惑；
 * role 刻意不含：授予/撤销管理员一律改库，接口上开这个口子等于给自己留提权后门。
 */
@Data
public class UserSaveRequest {

    @NotNull(message = "状态不能为空")
    private UserStatus status;

    /** 第一跳节点 id，null 表示不分配 */
    private Long frontNodeId;

    /** 落地节点 id，null 表示不分配 */
    private Long landNodeId;
}
