package ai.mintpop.lane.enumeration;

/**
 * 用户角色。权限属于本系统而非身份提供方：Logto 只回答「你是谁」，
 * 能不能进管理端由这个字段决定。授予管理员靠手动改库，管理端不提供编辑入口。
 */
public enum UserRole {

    /** 管理员：可访问 /api/admin/** */
    ADMIN,

    /** 普通成员：只能用终端拉链路 */
    MEMBER
}
