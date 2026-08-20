/**
 * 服务端登录入口：整页跳这里由服务端发起 OIDC 握手，回来时已带会话 Cookie。
 * 单一来源——store 的 signIn、http 层的 401 兜底、登录失败页的「重试登录」都引用这一个常量，
 * 路径一旦变动只改这里。
 */
export const 登录入口 = "/oauth2/authorization/logto";
