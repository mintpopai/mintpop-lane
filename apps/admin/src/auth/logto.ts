import LogtoClient from "@logto/browser";
import { runtimeConfig } from "../config/runtime";

let client: LogtoClient | null = null;

/**
 * Logto 客户端单例。
 * `@logto/browser` 的 LogtoClient 是**默认导出**（包里没有同名具名导出），
 * 写成 `import { LogtoClient }` 会在构建期报找不到导出。
 */
export function logtoClient(): LogtoClient {
  if (!client) {
    const config = runtimeConfig();
    client = new LogtoClient({
      endpoint: config.logtoEndpoint,
      appId: config.logtoAppId,
      // 带上 resource 才能拿到 audience 是本服务的 access token
      resources: [config.apiResource],
    });
  }
  return client;
}

/** 登录回调地址。这两个地址都要在 Logto 的 SPA 应用里逐字注册 */
export function callbackUri(): string {
  return `${window.location.origin}/callback`;
}

/** 退出登录后回到的地址 */
export function postSignOutUri(): string {
  return `${window.location.origin}/`;
}

/** 取调管理接口用的 access token，静默刷新由 SDK 负责 */
export function accessToken(): Promise<string> {
  return logtoClient().getAccessToken(runtimeConfig().apiResource);
}
