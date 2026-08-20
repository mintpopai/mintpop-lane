import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { 登录入口 } from "../auth/constants";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";

/**
 * 登录态完全由服务端会话 Cookie 承载，前端不持有任何 token。
 * 「我是谁、是不是管理员」都从 /api/me 读；真正的权限强制层是服务端的 403，
 * 这里的 isAdmin 只用于路由与界面展示。
 */
export const useAuthStore = defineStore("auth", () => {
  const me = ref<MeResponse | null>(null);
  const probed = ref(false);

  const authenticated = computed(() => me.value !== null);
  /** null 表示还没探过（探测失败也算没探过，下次导航重试） */
  const isAdmin = computed<boolean | null>(() =>
    probed.value ? (me.value ? me.value.role === "ADMIN" : false) : null,
  );
  const displayName = computed(() => me.value?.name || me.value?.email || "");

  /**
   * 用 /api/me 同步一次登录态。401 = 没登录（返回 false，不抛）；
   * 其它异常原样抛出——网络抖动不能被误判成没登录，否则会平白把人踢去 Logto。
   */
  async function refreshAuthState(api: AuthApi): Promise<boolean> {
    try {
      me.value = await api.me();
      probed.value = true;
      return true;
    } catch (error) {
      if (error instanceof UnauthorizedError) {
        me.value = null;
        probed.value = true;
        return false;
      }
      throw error;
    }
  }

  /** 整页跳服务端登录入口，握手由服务端完成，回来时已带会话 Cookie */
  function signIn(): void {
    window.location.assign(登录入口);
  }

  /** 整页跳服务端登出端点，清 Cookie 后 302 回管理端首页 */
  function signOut(): void {
    me.value = null;
    probed.value = false;
    window.location.assign("/auth/logout");
  }

  return { me, authenticated, isAdmin, displayName, refreshAuthState, signIn, signOut };
});
