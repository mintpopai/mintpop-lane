import { defineStore } from "pinia";
import { ref } from "vue";
import type { AdminApi } from "../api/admin";
import { ForbiddenError } from "../api/http";
import { callbackUri, logtoClient, postSignOutUri } from "../auth/logto";

export const useAuthStore = defineStore("auth", () => {
  const authenticated = ref(false);
  /** null 表示还没探过。权限只能由服务端的 403 得出，前端不解析 token */
  const isAdmin = ref<boolean | null>(null);
  const displayName = ref("");

  /** 同步一次 Logto 的登录态与显示名 */
  async function refreshAuthState(): Promise<boolean> {
    const client = logtoClient();
    authenticated.value = await client.isAuthenticated();
    if (authenticated.value && !displayName.value) {
      const claims = await client.getIdTokenClaims();
      displayName.value = claims.name ?? claims.username ?? claims.email ?? claims.sub;
    }
    return authenticated.value;
  }

  /**
   * 用一次最便宜的管理接口探权限。
   * 403 = 库里的 role 不是 ADMIN；其它异常原样抛出，交给调用方提示，
   * 绝不把网络问题误判成没权限。
   */
  async function probeAdmin(api: AdminApi): Promise<void> {
    if (isAdmin.value !== null) {
      return;
    }
    try {
      await api.listNodes();
      isAdmin.value = true;
    } catch (error) {
      if (error instanceof ForbiddenError) {
        isAdmin.value = false;
        return;
      }
      throw error;
    }
  }

  async function signIn(): Promise<void> {
    await logtoClient().signIn(callbackUri());
  }

  async function signOut(): Promise<void> {
    authenticated.value = false;
    isAdmin.value = null;
    displayName.value = "";
    await logtoClient().signOut(postSignOutUri());
  }

  return { authenticated, isAdmin, displayName, refreshAuthState, probeAdmin, signIn, signOut };
});
