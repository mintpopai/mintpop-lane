package ai.mintpop.lane.enumeration;

/** 用户在终端体系中的状态。取值与成员名逐字一致，客户端镜像同一套名字。 */
public enum UserStatus {

    /** 正常可用 */
    ACTIVE,

    /** 临时停用，可恢复 */
    SUSPENDED,

    /** 已吊销，终态 */
    REVOKED
}
