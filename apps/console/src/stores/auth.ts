import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { loginEntryUrl } from "../auth/constants";
import { markLoginRedirect } from "../utils/loginLoop";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";

/**
 * 登录态完全由服务端会话 Cookie 承载，前端不持有任何 token。
 * 控制台没有角色概念：登录了就能用，管理员账号也一样是买家。「我是谁、有哪些订阅」都从 /api/me 读。
 */
export const useAuthStore = defineStore("auth", () => {
  const me = ref<MeResponse | null>(null);

  const authenticated = computed(() => me.value !== null);
  /** 系统里没有「用户名」，邮箱就是称呼当前登录者的唯一方式 */
  const email = computed(() => me.value?.email ?? "");

  /** 用 /api/me 同步一次登录态。401 = 没登录（返回 false）；其它异常原样抛出，网络抖动不能被误判成没登录 */
  async function refreshAuthState(api: AuthApi): Promise<boolean> {
    try {
      me.value = await api.me();
      return true;
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        me.value = null;
        return false;
      }
      throw error;
    }
  }

  /** 整页跳服务端登录入口；跳转前打环路标记，回来仍未登录时守卫据此熔断 */
  function signIn(): void {
    markLoginRedirect();
    window.location.assign(loginEntryUrl);
  }

  /** 整页跳服务端登出端点，清 Cookie 后回控制台首页 */
  function signOut(): void {
    me.value = null;
    window.location.assign("/auth/logout");
  }

  return { me, authenticated, email, refreshAuthState, signIn, signOut };
});
