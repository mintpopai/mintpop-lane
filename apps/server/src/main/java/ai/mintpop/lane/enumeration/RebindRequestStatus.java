package ai.mintpop.lane.enumeration;

/** 换机申请的状态。四态都是终态或待办，没有中间态 */
public enum RebindRequestStatus {

    /** 待管理员处理。一份订阅同时只允许有一条，由服务层保证 */
    PENDING,

    /** 管理员已同意，订阅已改绑到 to_device_id */
    APPROVED,

    /** 管理员已拒绝，订阅继续绑在原设备上 */
    REJECTED,

    /**
     * 已作废：同一份订阅又提了新申请，或管理员直接解绑了这份订阅。
     * 与 REJECTED 分开——那是管理员的判断，这只是被后续动作顶掉，不该读成「被拒绝过」
     */
    SUPERSEDED;

    /** 只有待处理的申请才可被裁决 */
    public boolean isPending() {
        return this == PENDING;
    }
}
