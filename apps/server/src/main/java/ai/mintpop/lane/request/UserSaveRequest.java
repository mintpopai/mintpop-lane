package ai.mintpop.lane.request;

import ai.mintpop.lane.enumeration.FrontAction;
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
     * 这次保存要对第一跳（前置节点）做什么：{@link FrontAction} 的三态之一。
     * 第一跳不能手工指定节点，只能自动分配或清空，所以入参里没有节点 id。
     * <p>
     * <b>刻意必填</b>：这个接口是整体保存，每个调用点都必须显式表态。缺省值会让「忘了传」
     * 悄悄落到某一种处置上——二期出事正是这个形态（`frontNodeId` 一个字段兼职两种意图，
     * 于是每一次常规保存都被读成「管理员显式指定了单个节点」，把按故障域分散好的一组静默砍成一个）。
     * 现在「忘了传」会在 400 上当场暴露。
     */
    @NotNull(message = "第一跳处置不能为空")
    private FrontAction frontAction;


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
