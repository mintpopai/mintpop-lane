package ai.mintpop.lane.enumeration;

/**
 * 管理端这次保存要对用户的第一跳（前置节点）做什么。
 * <p>
 * 它表达的是<b>管理员的意图</b>，由调用方显式给出，服务端不从任何取值去反推——
 * 更新用户是<b>整体保存</b>接口（改备注、停用、恢复、只改落地节点，每一次都把用户对象
 * 全部字段原样 POST 回来），从回填的取值反推意图必然出错：调用方带回的可能是过期快照，
 * 而「这次没动第一跳」与「钉死到当前这个节点」在取值上根本无法区分。
 * <p>
 * 与 {@code AdminUserServiceImpl.FrontGroupWrite}（对 user_front_node 这张表做什么）
 * 是两层：这里是「管理员想干什么」，那里是「据此对表做什么」，别把两者合并。
 */
public enum FrontAction {

    /**
     * 这次保存没有动第一跳：user_front_node 原样不碰，{@code front_node_id} 沿用库里现值。
     * <p>
     * 它之所以必须作为一个<b>显式</b>取值存在：这是整体保存接口，绝大多数保存（改备注、
     * 停用/恢复/吊销、只改落地节点）压根不打算动第一跳，接口上必须有一句「本项未被触碰」
     * 能说出口。否则就只能靠「入参与库里现值是否相同」去猜，而调用方带回的值可能是过期快照
     * （列表页打开之后该用户的第一跳被别人改过），一猜就会把按故障域分散好的一组静默砍成一个。
     */
    KEEP,

    /**
     * 按故障域重新分配一组前置节点（见 {@link ai.mintpop.lane.service.FrontNodeAllocator}）：
     * 算出的整组写入 user_front_node，其中的主节点写回 {@code front_node_id}。
     * 忽略 {@code frontNodeId}。一个候选都算不出来时报错而不是清空——
     * 管理员显式要了「自动分配」，不能给他「把人下线」。
     */
    AUTO,

    /**
     * 钉死到 {@code frontNodeId} 指定的那一个节点（运维逃生口）：user_front_node 收敛成单元素，
     * {@code front_node_id} 写该 id。此时 {@code frontNodeId} 必填。
     * <p>
     * 钉死到该用户<b>当前的主节点</b>同样是一条合法意图（把已有的多节点组收敛成一个），
     * 这正是「从取值反推意图」表达不出来的那种情形。
     */
    PIN,

    /**
     * 真的不分配：user_front_node 删空，{@code front_node_id} 置 null。
     * 这是取消分配、腾出节点以便删除的唯一入口。忽略 {@code frontNodeId}。
     */
    CLEAR
}
