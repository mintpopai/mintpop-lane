import { 登录页路径 } from "../auth/constants";
import { runtimeConfig } from "../config/runtime";
import { createAdminApi, type AdminApi } from "./admin";
import { createAuthApi, type AuthApi } from "./auth";
import { createHttpClient, type HttpClient } from "./http";

let http: HttpClient | null = null;
let admin: AdminApi | null = null;
let auth: AuthApi | null = null;

/**
 * 组装层：凭据在 HttpOnly Cookie 里，浏览器自动携带，这里只接「接口前缀」
 * 与「会话失效跳登录」两个运行时依赖。业务代码只依赖接口，测试直接传假实现。
 */
function httpClient(): HttpClient {
  if (!http) {
    http = createHttpClient({
      baseUrl: runtimeConfig().apiBaseUrl,
      // 会话失效就整页落回登录落地页（不静默跳 Logto），由用户主动点「登录」
      onUnauthorized: () => window.location.assign(登录页路径),
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
