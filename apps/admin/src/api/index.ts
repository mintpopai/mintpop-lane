import { loginPagePath } from "../auth/constants";
import { createAdminApi, type AdminApi } from "./admin";
import { createAuthApi, type AuthApi } from "./auth";
import { createHttpClient, type HttpClient } from "./http";

/**
 * 接口前缀是常量而非配置：前缀由服务端路由写死（@RequestMapping("/api/...")），
 * 且管理端强制与 API 同源分路径部署（本地 Vite 代理、线上反代都原样转发 /api），
 * 任何环境下它都只能是 /api，没有“换环境要换值”的场景。
 */
const apiBaseUrl = "/api";

let http: HttpClient | null = null;
let admin: AdminApi | null = null;
let auth: AuthApi | null = null;

/**
 * 组装层：凭据在 HttpOnly Cookie 里，浏览器自动携带，这里只接「会话失效跳登录」
 * 一个运行时依赖。业务代码只依赖接口，测试直接传假实现。
 */
function httpClient(): HttpClient {
  if (!http) {
    http = createHttpClient({
      baseUrl: apiBaseUrl,
      // 会话失效就整页落回登录落地页（不静默跳 Logto），由用户主动点「登录」
      onUnauthorized: () => window.location.assign(loginPagePath),
    });
  }
  return http;
}

export function adminApi(): AdminApi {
  if (!admin) {
    admin = createAdminApi(httpClient());
  }
  return admin;
}

export function authApi(): AuthApi {
  if (!auth) {
    auth = createAuthApi(httpClient());
  }
  return auth;
}
