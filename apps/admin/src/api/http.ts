import type { ApiResponse } from "./types";

/** 业务失败：HTTP 200，但返回体的 code 非 0 */
export class BizError extends Error {
  constructor(
    readonly code: number,
    message: string,
  ) {
    super(message);
    this.name = "BizError";
  }
}

/** 401：token 无效或过期，需要重新登录 */
export class UnauthorizedError extends Error {
  constructor(message = "登录状态已失效，请重新登录") {
    super(message);
    this.name = "UnauthorizedError";
  }
}

/** 403：token 有效但库里的角色不是 ADMIN */
export class ForbiddenError extends Error {
  constructor(message = "当前账号没有管理权限") {
    super(message);
    this.name = "ForbiddenError";
  }
}

export interface HttpClientOptions {
  /** 接口前缀，如 /api */
  baseUrl: string;
  /** 注入点，测试里换成假的 */
  fetchImpl?: typeof fetch;
  /** 会话失效时的回调（401）。注入点：装配层接上「重新登录」 */
  onUnauthorized?: () => void;
}

export interface HttpClient {
  request<T>(path: string, init?: RequestInit): Promise<T>;
}

/**
 * 建一个只做两件事的 HTTP 客户端：认 401/403、拆 ApiResponse。
 * 凭据在 HttpOnly Cookie 里，同域请求浏览器自动携带，不需要也不能由前端注入。
 * 依赖全部由外部传入，因此单测不需要打模块补丁。
 */
export function createHttpClient({ baseUrl, fetchImpl, onUnauthorized }: HttpClientOptions): HttpClient {
  const doFetch: typeof fetch = fetchImpl ?? ((input, init) => globalThis.fetch(input, init));

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    try {
      const response = await doFetch(`${baseUrl}${path}`, {
        ...init,
        headers: {
          "Content-Type": "application/json",
          ...(init.headers as Record<string, string> | undefined),
        },
      });

      // 认证与授权失败走原生状态码，走不到业务码，必须在拆包之前分流
      if (response.status === 401) {
        throw new UnauthorizedError();
      }
      if (response.status === 403) {
        throw new ForbiddenError();
      }
      if (!response.ok) {
        throw new Error(`服务端返回异常状态：${response.status}`);
      }

      const body = (await response.json()) as ApiResponse<T>;
      if (body.code !== 0) {
        throw new BizError(body.code, body.msg ?? "请求失败");
      }
      return body.data as T;
    } catch (error) {
      // 会话失效统一在这一处兜住：服务端回的 401。
      // 仍然把异常抛出去，让调用方该提示提示、该中断中断
      if (error instanceof UnauthorizedError) {
        onUnauthorized?.();
      }
      throw error;
    }
  }

  return { request };
}
