import type { HttpClient } from "./http";
import type { MeResponse } from "./types";

export interface AuthApi {
  /** 当前登录者信息。401 会由 http 层抛 UnauthorizedError */
  me(): Promise<MeResponse>;
}

/** 会话接口的薄封装。http 由外部传入，测试里换成假的即可 */
export function createAuthApi(http: HttpClient): AuthApi {
  return {
    me() {
      return http.request("/me");
    },
  };
}
