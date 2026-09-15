/**
 * 服务端登录入口：整页跳这里由服务端发起 OIDC 握手，回来时已带会话 Cookie。
 * 唯一使用方是 store 的 signIn（登录落地页与登录失败页的按钮都走它），路径一旦变动只改这里。
 */
export const loginEntryUrl = "/oauth2/authorization/logto";

/**
 * 前端登录落地页路径：未登录时先落到这里，由用户主动点「登录」再去 Logto，
 * 不做静默跳转。路由表与 http 层 401 兜底共用这一个常量。
 */
export const loginPagePath = "/login";
