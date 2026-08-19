import { accessToken, callbackUri, logtoClient } from "../auth/logto";
import { runtimeConfig } from "../config/runtime";
import { createAdminApi, type AdminApi } from "./admin";
import { createHttpClient } from "./http";

let api: AdminApi | null = null;

/**
 * 组装层：把「取 token」与「接口前缀」这两个运行时依赖接到 HTTP 客户端上。
 * 业务代码只依赖 AdminApi 这个接口，测试里直接传假实现，不碰这里。
 */
export function adminApi(): AdminApi {
  if (!api) {
    api = createAdminApi(
      createHttpClient({
        baseUrl: runtimeConfig().apiBaseUrl,
        getToken: accessToken,
        // 会话失效就直接重走一次 PKCE 登录。signIn 会跳转离开本页，
        // 故不必等待它返回，也不必处理它的结果
        onUnauthorized: () => void logtoClient().signIn(callbackUri()),
      }),
    );
  }
  return api;
}
