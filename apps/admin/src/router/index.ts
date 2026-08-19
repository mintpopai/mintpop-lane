import { ElMessage } from "element-plus";
import { createRouter, createWebHistory, type Router } from "vue-router";
import { adminApi } from "../api";
import { useAuthStore } from "../stores/auth";
import AppLayout from "../layouts/AppLayout.vue";
import CallbackView from "../views/CallbackView.vue";
import ForbiddenView from "../views/ForbiddenView.vue";
import NodesView from "../views/NodesView.vue";
import UsersView from "../views/UsersView.vue";

export function createAppRouter(): Router {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      // public 的两页不进登录守卫：回调页正是用来完成登录的，
      // 无权限页则必须在 isAdmin=false 时还能打开，否则会来回跳
      { path: "/callback", name: "CALLBACK", component: CallbackView, meta: { public: true } },
      { path: "/forbidden", name: "FORBIDDEN", component: ForbiddenView, meta: { public: true } },
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
    if (to.meta.public) {
      return true;
    }

    const auth = useAuthStore();
    if (!(await auth.refreshAuthState())) {
      // 跳去 Logto 授权页，本次导航不再继续
      await auth.signIn();
      return false;
    }

    try {
      await auth.probeAdmin(adminApi());
    } catch (error) {
      // 非 403 的异常（网络抖动、服务端 5xx）不该把人赶去「无管理权限」页，
      // 也不该让导航整个失败变白屏：放行进页面，由页面自己的加载错误去提示，
      // isAdmin 仍是 null，下次导航会重试
      ElMessage.error(`校验管理权限失败：${(error as Error).message}`);
      return true;
    }
    return auth.isAdmin ? true : { name: "FORBIDDEN" };
  });

  return router;
}
