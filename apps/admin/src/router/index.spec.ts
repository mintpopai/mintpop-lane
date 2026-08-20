import { createPinia, setActivePinia } from "pinia";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createMemoryHistory, isNavigationFailure, NavigationFailureType } from "vue-router";
import type { AuthApi } from "../api/auth";
import { UnauthorizedError } from "../api/http";
import type { MeResponse } from "../api/types";
import { 标记登录跳转 } from "../utils/loginLoop";
import { createAppRouter } from "./index";

const 管理员: MeResponse = { id: 1, email: "a@b.c", name: "甲", role: "ADMIN", subscriptions: [] };
const 普通成员: MeResponse = { id: 2, email: "m@b.c", name: "乙", role: "MEMBER", subscriptions: [] };

function 假api(result: MeResponse | Error): AuthApi {
  return {
    me: async () => {
      if (result instanceof Error) throw result;
      return result;
    },
  };
}

function 建路由(result: MeResponse | Error) {
  return createAppRouter(假api(result), createMemoryHistory());
}

let 原始location: Location;
let assign跳转: ReturnType<typeof vi.fn>;

beforeEach(() => {
  setActivePinia(createPinia());
  sessionStorage.clear();
  // jsdom 的 location.assign 是 not implemented，会抛错；换成可断言的桩
  原始location = window.location;
  assign跳转 = vi.fn();
  Object.defineProperty(window, "location", {
    value: { ...原始location, assign: assign跳转 },
    writable: true,
    configurable: true,
  });
});

afterEach(() => {
  Object.defineProperty(window, "location", {
    value: 原始location,
    writable: true,
    configurable: true,
  });
});

describe("路由守卫", () => {
  it("管理员放行进目标页", async () => {
    const router = 建路由(管理员);

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("USERS");
  });

  it("已登录但不是管理员落到无权限页", async () => {
    const router = 建路由(普通成员);

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("FORBIDDEN");
  });

  it("未登录（401）时中断导航并整页跳登录入口", async () => {
    const router = 建路由(new UnauthorizedError());

    const failure = await router.push("/users");

    expect(isNavigationFailure(failure, NavigationFailureType.aborted)).toBe(true);
    expect(assign跳转).toHaveBeenCalledWith("/oauth2/authorization/logto");
    // 跳转前打了环路标记，下一次再回到未登录才能被熔断认出来
    expect(sessionStorage.getItem("lane.loginRedirectAt")).not.toBeNull();
  });

  it("刚跳过登录又回到未登录：熔断到登录失败页，不再跳登录", async () => {
    标记登录跳转(Date.now());
    const router = 建路由(new UnauthorizedError());

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("LOGIN_ERROR");
    expect(assign跳转).not.toHaveBeenCalled();
  });

  it("探测抛普通异常时放行，不把人赶去登录页或无权限页", async () => {
    const router = 建路由(new Error("网络错误"));

    await router.push("/users");

    expect(router.currentRoute.value.name).toBe("USERS");
    expect(assign跳转).not.toHaveBeenCalled();
  });

  it("服务端带 ?login_error=1 回来时直接落登录失败页", async () => {
    const router = 建路由(管理员);

    await router.push("/users?login_error=1");

    expect(router.currentRoute.value.name).toBe("LOGIN_ERROR");
  });

  it("登录成功会清掉环路标记，避免下次登录被误判", async () => {
    标记登录跳转(Date.now());
    const router = 建路由(管理员);

    await router.push("/users");

    expect(sessionStorage.getItem("lane.loginRedirectAt")).toBeNull();
  });
});
