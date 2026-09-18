package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    /**
     * 第一跳（前置）主节点 id，语义就是字面意思：**null 表示不分配**（连同整组一起清空），
     * 具体 id 表示手工指定这一个节点——这是运维逃生口，手工指定仍然允许。
     * <p>
     * 本字段刻意**不再**兼职表达「自动分配」：那是 {@link #reallocateFront} 的职责。
     * 二期上线时两种意图曾挤在这一个字段里（null＝自动分配），而这个接口是整体保存、
     * 调用方每次都把现值原样带回来，于是「改备注」「停用/恢复」这类保存全都被当成
     * 「管理员显式指定了单个节点」，把按故障域分散好的一组静默砍成一个。
     * <p>
     * 因为是整体保存，本字段与库里现值相同即「这次没有动第一跳」，前置节点组原样不动。
     */
    private Long frontNodeId;

    /**
     * 是否按故障域重新分配一组前置节点（见 {@link ai.mintpop.lane.service.FrontNodeAllocator}）。
     * <p>
     * 这是一个**动作**而不是状态：为真时忽略 {@link #frontNodeId}，算出的组整体写入
     * user_front_node，其中的主节点写回 {@code front_node_id}。缺省 false——
     * 没有显式要求就绝不重算，管理端那些只改备注/状态/落地节点的保存因此碰不到前置组。
     */
    private boolean reallocateFront;

    /** 落地节点 id，null 表示不分配 */
    private Long landNodeId;

    /**
     * 备注，管理员自用说明；null 表示清空。
     * 这个接口是整体保存，调用方每次都要把现值带回来，否则会被这次提交抹掉。
     * <p>
     * 上限 50 是业务规则、权威位置在这里，与库列宽度（VARCHAR(255)，与其它表的
     * remark 列同构）刻意不一致——备注要的是「一眼读完的一句话」，长过这个尺度
     * 的内容不该往这里塞，也会在用户列表里被截断成省略号。
     */
    @Size(max = 50, message = "备注最多 50 字")
    private String remark;
}
