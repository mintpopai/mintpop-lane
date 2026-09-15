import { loginPagePath } from "../auth/constants";
import { createAuthApi, type AuthApi } from "./auth";
import { createConsoleApi, type ConsoleApi } from "./console";
import { createHttpClient, type HttpClient } from "./http";

/** 接口前缀是常量：服务端路由写死 /api，控制台强制与 API 同源分路径部署 */
const apiBaseUrl = "/api";

let http: HttpClient | null = null;
let auth: AuthApi | null = null;
let console_: ConsoleApi | null = null;

function httpClient(): HttpClient {
  if (!http) {
    http = createHttpClient({
      baseUrl: apiBaseUrl,
      // 会话失效就整页落回登录落地页，由用户主动点「登录」
      onUnauthorized: () => window.location.assign(loginPagePath),
    });
  }
  return http;
}

export function authApi(): AuthApi {
  if (!auth) {
    auth = createAuthApi(httpClient());
  }
  return auth;
}

export function consoleApi(): ConsoleApi {
  if (!console_) {
    console_ = createConsoleApi(httpClient());
  }
  return console_;
}
