import { ElMessage } from "element-plus";
import { createRouter, createWebHistory, type Router } from "vue-router";
import { authApi } from "../api";
import { useAuthStore } from "../stores/auth";
import AppLayout from "../layouts/AppLayout.vue";
import ForbiddenView from "../views/ForbiddenView.vue";
import NodesView from "../views/NodesView.vue";
import UsersView from "../views/UsersView.vue";

export function createAppRouter(): Router {
  const router = createRouter({
    history: createWebHistory(),
    routes: [
      // 无权限页必须在 isAdmin=false 时还能打开，否则会来回跳
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
    try {
      if (!(await auth.refreshAuthState(authApi()))) {
        // 未登录：整页跳服务端登录入口，本次导航不再继续
        auth.signIn();
        return false;
      }
    } catch (error) {
      // 网络抖动、服务端 5xx 不该把人赶去登录页或无权限页，
      // 放行进页面由页面自己的加载错误提示，下次导航重试
      ElMessage.error(`获取登录状态失败：${(error as Error).message}`);
      return true;
    }
    return auth.isAdmin ? true : { name: "FORBIDDEN" };
  });

  return router;
}
