import { createRouter, createWebHistory, type Router, type RouterHistory } from "vue-router";
import { authApi } from "../api";
import { showToast } from "../toast";
import type { AuthApi } from "../api/auth";
import { useAuthStore } from "../stores/auth";
import { 登录页路径 } from "../auth/constants";
import { 清除标记, 疑似环路 } from "../utils/loginLoop";
import AppLayout from "../layouts/AppLayout.vue";
import ForbiddenView from "../views/ForbiddenView.vue";
import LoginErrorView from "../views/LoginErrorView.vue";
import LoginView from "../views/LoginView.vue";
import NodesView from "../views/NodesView.vue";
import UsersView from "../views/UsersView.vue";

/**
 * 两个依赖都做成可选入参：api 让守卫可被注入假实现，history 让测试用内存历史。
 * 生产调用处（main.ts）不传参，行为与从前一致。
 */
export function createAppRouter(
  api: AuthApi = authApi(),
  history: RouterHistory = createWebHistory(),
): Router {
  const router = createRouter({
    history,
    routes: [
      // 登录落地页：未登录的落点，必须 public，否则会被守卫送回自己
      { path: 登录页路径, name: "LOGIN", component: LoginView, meta: { public: true } },
      // 无权限页必须在 isAdmin=false 时还能打开，否则会来回跳
      { path: "/forbidden", name: "FORBIDDEN", component: ForbiddenView, meta: { public: true } },
      // 登录环路的落点：同样必须 public，否则它自己也会被守卫赶去登录
      { path: "/login-error", name: "LOGIN_ERROR", component: LoginErrorView, meta: { public: true } },
      {
        path: "/",
        component: AppLayout,
        children: [
          { path: "", redirect: { name: "USERS" } },
          { path: "users", name: "USERS", component: UsersView },
          { path: "nodes", name: "NODES", component: NodesView },
        ],
      },
      { path: "/:pathMatch(.*)*", redirect: { name: "USERS" } },
    ],
  });

  router.beforeEach(async (to) => {
    // 服务端握手失败时会带 ?login_error=1 回到管理端，先于任何探测把人送到能读懂的错误页。
    // 跳转会丢掉这个查询参数，因此不会自我循环
    if (to.query.login_error === "1" && to.name !== "LOGIN_ERROR") {
      return { name: "LOGIN_ERROR" };
    }

    if (to.meta.public) {
      return true;
    }

    const auth = useAuthStore();
    try {
      // 每次导航都实探 /api/me 是有意为之——吊销/停用在下一次导航即生效，勿加缓存
      if (!(await auth.refreshAuthState(api))) {
        // 刚点过登录又立刻回到未登录：会话没生效，落错误页说明原因，别再送落地页绕圈
        if (疑似环路()) {
          return { name: "LOGIN_ERROR" };
        }
        // 未登录：落到登录落地页，由用户主动点「登录」再去 Logto，不做静默跳转
        return { name: "LOGIN" };
      }
      清除标记();
    } catch (error) {
      // 网络抖动、服务端 5xx 不该把人赶去登录页或无权限页，
      // 放行进页面由页面自己的加载错误提示，下次导航重试
      showToast("error", `获取登录状态失败：${(error as Error).message}`);
      return true;
    }
    return auth.isAdmin ? true : { name: "FORBIDDEN" };
  });

  return router;
}
