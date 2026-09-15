import { createRouter, createWebHistory, type Router, type RouterHistory } from "vue-router";
import { authApi } from "../api";
import { showToast } from "../toast";
import type { AuthApi } from "../api/auth";
import { useAuthStore } from "../stores/auth";
import { loginPagePath } from "../auth/constants";
import { clearLoginMark, isLikelyLoginLoop } from "../utils/loginLoop";
import AppLayout from "../layouts/AppLayout.vue";
import LoginErrorView from "../views/LoginErrorView.vue";
import LoginView from "../views/LoginView.vue";
import OrdersView from "../views/OrdersView.vue";
import PayView from "../views/PayView.vue";
import PaymentResultView from "../views/PaymentResultView.vue";
import PlansView from "../views/PlansView.vue";
import SubscriptionsView from "../views/SubscriptionsView.vue";

/** api 与 history 都做成可选入参：守卫可注入假实现，测试用内存历史。生产（main.ts）不传参 */
export function createAppRouter(
  api: AuthApi = authApi(),
  history: RouterHistory = createWebHistory(),
): Router {
  const router = createRouter({
    history,
    routes: [
      { path: loginPagePath, name: "LOGIN", component: LoginView, meta: { public: true } },
      {
        path: "/login-error",
        name: "LOGIN_ERROR",
        component: LoginErrorView,
        meta: { public: true },
      },
      {
        path: "/",
        component: AppLayout,
        children: [
          { path: "", name: "SUBSCRIPTIONS", component: SubscriptionsView },
          { path: "plans", name: "PLANS", component: PlansView },
          { path: "orders", name: "ORDERS", component: OrdersView },
          { path: "pay/:orderNo", name: "PAY", component: PayView },
          { path: "payment/result", name: "PAYMENT_RESULT", component: PaymentResultView },
        ],
      },
      { path: "/:pathMatch(.*)*", redirect: { name: "SUBSCRIPTIONS" } },
    ],
  });

  router.beforeEach(async (to) => {
    // 服务端握手失败会带 ?login_error=1 回来，先于任何探测把人送到能读懂的错误页
    if (to.query.login_error === "1" && to.name !== "LOGIN_ERROR") {
      return { name: "LOGIN_ERROR" };
    }
    if (to.meta.public) {
      return true;
    }
    const auth = useAuthStore();
    try {
      // 每次导航都实探 /api/me：停用 / 吊销在下一次导航即生效，勿加缓存
      if (!(await auth.refreshAuthState(api))) {
        if (isLikelyLoginLoop()) {
          return { name: "LOGIN_ERROR" };
        }
        return { name: "LOGIN" };
      }
      clearLoginMark();
    } catch (error) {
      // 网络抖动、服务端 5xx 不该把人赶去登录页，放行由页面自己提示，下次导航重试
      showToast("error", `获取登录状态失败：${(error as Error).message}`);
    }
    // 登录即放行：控制台没有角色门槛
    return true;
  });

  return router;
}
